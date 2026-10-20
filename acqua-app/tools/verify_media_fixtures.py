"""Small, offline media-processing fixtures. No device or remote media is used."""
import argparse
import contextlib
import functools
import hashlib
import http.server
import io
import json
import re
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import threading
import urllib.parse

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "build" / "host-python"))

from yt_dlp import YoutubeDL, version
from yt_dlp.postprocessor.modify_chapters import ModifyChaptersPP
from yt_dlp.postprocessor.sponsorblock import SponsorBlockPP
from yt_dlp.postprocessor.ffmpeg import FFmpegEmbedSubtitlePP, FFmpegSubtitlesConvertorPP, FFmpegVideoRemuxerPP, FFmpegMergerPP


def command(*arguments):
    return subprocess.run(arguments, check=True, capture_output=True, text=True, timeout=30).stdout


def duration(file):
    data = command("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "json", str(file))
    return float(json.loads(data)["format"]["duration"])


def segment_fixtures(directory):
    fixture_id = "fixture00001"
    requested = []
    available = [dict(category="sponsor", segment=[2, 3], videoDuration=10, actionType="skip", description=""),
                 dict(category="intro", segment=[6, 7], videoDuration=10, actionType="skip", description="")]

    class API(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            request = urllib.parse.urlsplit(self.path)
            assert request.path.endswith(hashlib.sha256(fixture_id.encode()).hexdigest()[:4])
            categories = json.loads(urllib.parse.parse_qs(request.query)["categories"][0])
            requested.append(set(categories))
            rows = [row for row in available if row["category"] in categories]
            payload = json.dumps([dict(videoID=fixture_id, segments=rows)]).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

        def log_message(self, *_args):
            pass

    server = http.server.HTTPServer(("127.0.0.1", 0), API)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    results = []
    try:
        for extension in ("wav", "mp4"):
            original = directory / f"source.{extension}"
            if extension == "wav":
                command("ffmpeg", "-nostdin", "-v", "error", "-f", "lavfi", "-i", "sine=frequency=440:duration=10",
                        "-c:a", "pcm_s16le", str(original))
            else:
                command("ffmpeg", "-nostdin", "-v", "error", "-f", "lavfi", "-i", "testsrc=size=64x48:rate=5:duration=10",
                        "-pix_fmt", "yuv420p", "-c:v", "libx264", "-preset", "ultrafast", "-bf", "0", str(original))
            for categories in (set(), {"sponsor"}, {"sponsor", "intro"}):
                target = directory / f"{extension}-{len(categories)}.{extension}"
                shutil.copyfile(original, target)
                info = dict(id=fixture_id, title="Disposable fixture", extractor_key="Youtube", filepath=str(target),
                            ext=extension, duration=10, vcodec="none" if extension == "wav" else "h264",
                            chapters=[dict(start_time=0, end_time=5, title="First"), dict(start_time=5, end_time=10, title="Second")])
                before = len(requested)
                with YoutubeDL(dict(quiet=True, no_warnings=True, proxy="", ffmpeg_location=shutil.which("ffmpeg"))) as ydl:
                    if categories:
                        _, info = SponsorBlockPP(ydl, categories=categories, api=f"http://127.0.0.1:{server.server_port}").run(info)
                        assert requested[-1] == categories
                        _, info = ModifyChaptersPP(ydl, remove_sponsor_segments=categories, force_keyframes=extension == "mp4").run(info)
                    else:
                        assert len(requested) == before
                expected = 10 - len(categories)
                actual = duration(target)
                assert abs(actual - expected) < 0.25, (extension, categories, actual, expected)
                assert abs(info["duration"] - expected) < 0.001
                assert abs(info["chapters"][-1]["end_time"] - expected) < 0.001
                assert all(0 <= row["start_time"] < row["end_time"] <= expected for row in info["chapters"])
                results.append(dict(kind=extension, categories=sorted(categories), duration=actual, expected=expected))
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)
    return results


def format_fixtures(_directory):
    # Real format parsing and sorting against synthetic metadata, without HTTP.
    formats = [
        dict(format_id="h264", ext="mp4", vcodec="avc1.640028", acodec="none", width=1080, height=1920, fps=30, tbr=3000),
        dict(format_id="hevc", ext="mp4", vcodec="hvc1.1", acodec="none", width=720, height=1280, fps=60, tbr=2000),
        dict(format_id="av1", ext="mp4", vcodec="av01.0", acodec="none", width=1080, height=1920, fps=60, tbr=2500),
        dict(format_id="low", ext="mp4", vcodec="avc1.4d", acodec="none", width=360, height=640, fps=25, tbr=300),
        dict(format_id="audioA", ext="m4a", vcodec="none", acodec="mp4a.40.2", abr=128),
        dict(format_id="audioO", ext="webm", vcodec="none", acodec="opus", abr=160),
    ]
    cases = [
        ("compatibility", "bv*[vcodec~='^(?:avc|h264)']+ba[acodec~='^(?:mp4a|aac)']/b[vcodec~='^(?:avc|h264)'][acodec~='^(?:mp4a|aac)']", [], {"h264", "audioA"}),
        ("device", "bv*[vcodec~='^(?:av01|av1|hev|hvc|h265)']+ba/b[vcodec~='^(?:av01|av1|hev|hvc|h265)']", [], {"av1", "audioO"}),
        ("portrait cap", "bv*[width<=?720]+ba/b[width<=?720]", [], {"hevc", "audioO"}),
        ("lowest", "bv*+ba/b", ["+res", "+br", "+fps"], {"low", "audioA"}),
        ("custom frame rate", "bv*+ba/b", ["fps", "+br"], {"hevc", "audioA"}),
        ("source audio", "ba[acodec~='^(?:opus)']/b[acodec~='^(?:opus)']", [], {"audioO"}),
        ("exact overrides", "h264+audioA", ["fps"], {"h264", "audioA"}),
        ("silent portrait", "bv[width<=?720]", [], {"hevc"}),
        ("multiple exact audio", "h264+audioA+audioO", [], {"h264", "audioA", "audioO"}),
    ]
    results = []
    for name, selector, sorting, expected in cases:
        with YoutubeDL(dict(quiet=True, no_warnings=True, skip_download=True, simulate=True, format=selector, allow_multiple_audio_streams=True,
                            format_sort=sorting, format_sort_force=bool(sorting))) as ydl:
            info = ydl.process_video_result(dict(id="fixture", title="Fixture", extractor="fixture", webpage_url="https://example.invalid/fixture",
                                                 formats=[dict(row, url=f"https://example.invalid/{row['format_id']}") for row in formats]), download=False)
            selected = {row["format_id"] for row in info.get("requested_formats", [info])}
            assert selected == expected, (name, selected, expected)
            results.append(dict(case=name, selected=sorted(selected)))
    return results


def caption_fixtures(directory):
    source = directory / "caption.srt"
    source.write_text("1\n00:00:00,250 --> 00:00:01,500\nHello fixture\n\n2\n00:00:02,000 --> 00:00:02,750\nSecond line\n", encoding="utf-8")
    results = []
    with YoutubeDL(dict(quiet=True, no_warnings=True)) as ydl:
        for target in ("ass", "lrc", "vtt"):
            copy = directory / f"convert-{target}.en.srt"
            shutil.copyfile(source, copy)
            info = dict(filepath=str(directory / f"convert-{target}.mp4"), ext="mp4", requested_subtitles=dict(en=dict(filepath=str(copy), ext="srt")), __files_to_move={str(copy): str(copy)})
            _, converted = FFmpegSubtitlesConvertorPP(ydl, format=target).run(info)
            output = Path(converted["requested_subtitles"]["en"]["filepath"])
            assert output.is_file() and "Hello fixture" in output.read_text(encoding="utf-8-sig")
            results.append(dict(conversion=target, bytes=output.stat().st_size))
        for container, sub_format, keep in (("m4a", "srt", True), ("mkv", "ass", True), ("mp4", "srt", False), ("webm", "vtt", True)):
            file = directory / f"embed-{container}.{container}"
            if container == "m4a":
                command("ffmpeg", "-nostdin", "-v", "error", "-f", "lavfi", "-i", "sine=frequency=440:duration=3", "-c:a", "aac", str(file))
            else:
                codec = "libvpx-vp9" if container == "webm" else "libx264"
                command("ffmpeg", "-nostdin", "-v", "error", "-f", "lavfi", "-i", "testsrc=size=64x48:rate=5:duration=3", "-pix_fmt", "yuv420p", "-c:v", codec, str(file))
            subtitle = source if sub_format == "srt" else directory / f"convert-{sub_format}.en.{sub_format}"
            if container == "mp4":
                shutil.copyfile(file, directory / "remux-source.mp4")
            info = dict(filepath=str(file), ext=container, requested_subtitles=dict(en=dict(filepath=str(subtitle), ext=sub_format, name="English")))
            deleted, _ = FFmpegEmbedSubtitlePP(ydl, already_have_subtitle=keep).run(info)
            streams = json.loads(command("ffprobe", "-v", "error", "-show_streams", "-of", "json", str(file)))["streams"]
            assert sum(row["codec_type"] == "subtitle" for row in streams) == 1
            assert sum(row["codec_type"] == ("audio" if container == "m4a" else "video") for row in streams) == 1
            assert deleted == ([] if keep else [str(subtitle)])
            assert subtitle.is_file()
            assert abs(duration(file) - 3) < 0.3
            results.append(dict(embedding=container, subtitle=sub_format, retained_sidecar=keep))
        video = directory / "remux-source.mp4"
        _, remuxed = FFmpegVideoRemuxerPP(ydl, preferedformat="mkv").run(dict(filepath=str(video), ext="mp4"))
        streams = json.loads(command("ffprobe", "-v", "error", "-show_streams", "-of", "json", remuxed["filepath"]))["streams"]
        assert next(row for row in streams if row["codec_type"] == "video")["codec_name"] == "h264"
        results.append(dict(remux="mp4 to mkv", video_codec="h264"))
    return results


def stream_fixtures(directory):
    video = directory / "video.mp4"
    english = directory / "english.m4a"
    hindi = directory / "hindi.opus"
    command("ffmpeg", "-nostdin", "-v", "error", "-f", "lavfi", "-i", "testsrc=size=64x48:rate=5:duration=3", "-pix_fmt", "yuv420p", "-c:v", "libx264", str(video))
    command("ffmpeg", "-nostdin", "-v", "error", "-f", "lavfi", "-i", "sine=frequency=440:duration=3", "-c:a", "aac", "-metadata:s:a:0", "language=eng", str(english))
    command("ffmpeg", "-nostdin", "-v", "error", "-f", "lavfi", "-i", "sine=frequency=660:duration=3", "-c:a", "libopus", "-metadata:s:a:0", "language=hin", str(hindi))
    results = []
    with YoutubeDL(dict(quiet=True, no_warnings=True)) as ydl:
        for name, files, expected_audio in (("two audio tracks", [video, english, hindi], 2), ("silent video", [video], 0)):
            output = directory / ("multiple.mkv" if expected_audio else "silent.mkv")
            formats = [dict(filepath=str(file), vcodec="h264" if file == video else "none",
                            acodec="none" if file == video else "aac" if file == english else "opus", protocol="http") for file in files]
            FFmpegMergerPP(ydl).run(dict(filepath=str(output), ext="mkv", requested_formats=formats, __files_to_merge=[str(file) for file in files]))
            streams = json.loads(command("ffprobe", "-v", "error", "-show_streams", "-of", "json", str(output)))["streams"]
            audio = [row for row in streams if row["codec_type"] == "audio"]
            assert len(audio) == expected_audio
            assert len([row for row in streams if row["codec_type"] == "video"]) == 1
            if expected_audio:
                assert {row["codec_name"] for row in audio} == {"aac", "opus"}
                assert {row["tags"]["language"] for row in audio} == {"eng", "hin"}
            assert abs(duration(output) - 3) < 0.25
            results.append(dict(case=name, audio_tracks=len(audio), duration=duration(output)))
    return results


def template_fixtures(directory):
    source = directory / "source.mp4"
    command("ffmpeg", "-nostdin", "-v", "error", "-f", "lavfi", "-i", "testsrc=size=64x48:rate=5:duration=3",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=3", "-pix_fmt", "yuv420p", "-c:v", "libx264", "-c:a", "aac", str(source))
    (directory / "source.srt").write_text("1\n00:00:00,250 --> 00:00:01,500\nHello template\n", encoding="utf-8")

    class Files(http.server.SimpleHTTPRequestHandler):
        def log_message(self, *_args):
            pass

    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), functools.partial(Files, directory=str(directory)))
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    base = f"http://127.0.0.1:{server.server_port}"
    results = []
    try:
        for identity in ("first", "second"):
            output = io.StringIO()
            params = dict(quiet=True, no_warnings=True, proxy="", format="best[vcodec^=avc]/best",
                          outtmpl=str(directory / "outputs" / "%(id)s.%(ext)s"), writesubtitles=True,
                          subtitleslangs=["en"], forceprint=dict(after_move=["ACQUA_TEMPLATE:[%(vcodec)j,%(acodec)j]", "ACQUA_FILE:%(filepath)s"]))
            with contextlib.redirect_stdout(output), YoutubeDL(params) as ydl:
                info = ydl.process_video_result(dict(id=identity, title=identity, extractor="fixture", webpage_url=f"{base}/{identity}",
                    formats=[dict(format_id="source", url=f"{base}/source.mp4", ext="mp4", vcodec="avc1", acodec="aac")],
                    subtitles=dict(en=[dict(url=f"{base}/source.srt", ext="srt")])))
            lines = output.getvalue().splitlines()
            assert [line for line in lines if line.startswith("ACQUA_TEMPLATE:")] == ['ACQUA_TEMPLATE:["avc1","aac"]']
            media = Path(info["requested_downloads"][0]["filepath"])
            assert [line for line in lines if line.startswith("ACQUA_FILE:")] == [f"ACQUA_FILE:{media}"]
            assert media.is_file() and abs(duration(media) - 3) < 0.25
            assert media.with_suffix(".en.srt").is_file()
            results.append(dict(case=identity, media=media.name, sidecar=media.with_suffix(".en.srt").name))
        assert len(list((directory / "outputs").iterdir())) == 4
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)
    return results


def timeline_fixtures(directory):
    plans = json.loads((ROOT / "core" / "data" / "build" / "media-timeline-plans.json").read_text(encoding="utf-8"))
    results = []
    for plan in plans:
        case = directory / plan["name"]
        case.mkdir()
        for relative, content in plan["files"].items():
            file = case / relative
            assert file.resolve().is_relative_to(case.resolve())
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(content, encoding="utf-8")
        source = case / plan["source"]
        arguments = ["ffmpeg", "-nostdin", "-v", "error"]
        if not plan["audio"]:
            arguments += ["-f", "lavfi", "-i", "testsrc=size=64x48:rate=20:duration=10"]
        arguments += ["-f", "lavfi", "-i", "sine=frequency=440:duration=10"]
        if not plan["audio"]:
            arguments += ["-pix_fmt", "yuv420p", "-c:v", "libx264", "-preset", "ultrafast"]
        arguments += ["-c:a", "libmp3lame" if source.suffix == ".mp3" else "aac", str(source)]
        command(*arguments)
        # Execute the argument lists and edited caption/chapter files emitted by the production JVM processor.
        for args in plan["commands"]:
            command("ffmpeg", *[str(case / arg[2:]) if arg.startswith("@/") else arg for arg in args])
        for expected in plan["outputs"]:
            target = case / expected["file"]
            actual = json.loads(command("ffprobe", "-v", "error", "-show_streams", "-show_chapters", "-of", "json", str(target)))
            assert abs(duration(target) - expected["duration"]) < 0.15, (plan["name"], target.name, duration(target), expected["duration"])
            assert sum(row["codec_type"] == "audio" for row in actual["streams"]) == 1
            assert sum(row["codec_type"] == "subtitle" for row in actual["streams"]) == int(expected["subtitles"])
            chapters = actual.get("chapters", [])
            assert len(chapters) == len(expected["chapters"]), (plan["name"], chapters, expected["chapters"])
            for row, wanted in zip(chapters, expected["chapters"]):
                assert abs(float(row["start_time"]) - wanted["start"]) < 0.06
                assert abs(float(row["end_time"]) - wanted["end"]) < 0.06
            if expected["subtitles"]:
                subtitles = command("ffmpeg", "-nostdin", "-v", "error", "-i", str(target), "-map", "0:s:0", "-f", "srt", "-")
                assert "cue" in subtitles
                clocks = re.findall(r"(\d+):(\d+):(\d+),(\d+) --> (\d+):(\d+):(\d+),(\d+)", subtitles)
                assert len(clocks) == len(expected["cues"])
                for clock, cue in zip(clocks, expected["cues"]):
                    parts = list(map(int, clock))
                    start = parts[0] * 3600 + parts[1] * 60 + parts[2] + parts[3] / 1000
                    end = parts[4] * 3600 + parts[5] * 60 + parts[6] + parts[7] / 1000
                    assert abs(start - cue["start"]) < 0.08 and abs(end - cue["end"]) < 0.08, (plan["name"], start, end, cue)
            results.append(dict(case=plan["name"], file=target.name, duration=duration(target), chapters=len(chapters)))
        assert all((case / file).is_file() and (case / file).stat().st_size > 0 for file in plan["sidecars"])
    return results


def cookie_fixtures(directory):
    fixture = json.loads((ROOT / "core" / "data" / "build" / "cookie-profile-fixture.json").read_text(encoding="utf-8"))
    source = directory / "source.mp4"
    command("ffmpeg", "-nostdin", "-v", "error", "-f", "lavfi", "-i", "testsrc=size=64x48:rate=5:duration=1",
            "-pix_fmt", "yuv420p", "-c:v", "libx264", str(source))
    observed = []

    class Website(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            cookie = self.headers.get("Cookie")
            assert self.headers.get("User-Agent") == fixture["userAgent"]
            observed.append((self.path, cookie is not None))
            if self.path == "/private/start":
                assert cookie == "sid=native-fixture"
                self.send_response(302)
                self.send_header("Location", f"http://127.0.0.1:{server.server_port}/outside")
            elif self.path == "/outside":
                assert cookie is None
                self.send_response(302)
                self.send_header("Location", f"http://localhost:{server.server_port}/final.mp4")
            else:
                assert self.path == "/final.mp4" and cookie is None
                payload = source.read_bytes()
                self.send_response(200)
                self.send_header("Content-Type", "video/mp4")
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
                return
            self.end_headers()

        def log_message(self, *_args):
            pass

    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Website)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    cookies = directory / "cookies.txt"
    cookies.write_text("\n".join(["# Netscape HTTP Cookie File"] + fixture["rows"]), encoding="utf-8")
    try:
        with YoutubeDL(dict(quiet=True, no_warnings=True, proxy="", cookiefile=str(cookies), http_headers={"User-Agent": fixture["userAgent"]},
                            outtmpl=str(directory / "download.%(ext)s"))) as ydl:
            info = ydl.process_video_result(dict(id="fixture", title="Cookie fixture", extractor="fixture", webpage_url="http://127.0.0.1/fixture",
                formats=[dict(format_id="source", url=f"http://127.0.0.1:{server.server_port}/private/start", ext="mp4", vcodec="avc1", acodec="none")]))
        output = Path(info["requested_downloads"][0]["filepath"])
        assert output.is_file() and abs(duration(output) - 1) < 0.25
        assert observed == [("/private/start", True), ("/outside", False), ("/final.mp4", False)]
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)
    return [dict(authenticated=True, captured_user_agent=True, path_boundary=True, host_boundary=True)]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("suite", choices=["segments", "formats", "captions", "streams", "templates", "timelines", "cookies"])
    args = parser.parse_args()
    destination = (ROOT / "build" / "media-fixtures").resolve()
    assert destination.is_relative_to(ROOT.resolve())
    destination.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=f"{args.suite}-", dir=destination) as temporary:
        directory = Path(temporary).resolve()
        assert directory.is_relative_to(destination)
        results = {"segments": segment_fixtures, "formats": format_fixtures, "captions": caption_fixtures, "streams": stream_fixtures, "templates": template_fixtures, "timelines": timeline_fixtures, "cookies": cookie_fixtures}[args.suite](directory)
    print(json.dumps(dict(suite=args.suite, engine=version.__version__, checks=results), indent=2))


if __name__ == "__main__":
    main()
