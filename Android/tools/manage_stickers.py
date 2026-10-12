#!/usr/bin/env python3
"""
StickerWA Manager & Scalable Build Tool for WhatsApp Stickers.
Validates, optimizes, converts GIF/images, and generates contents.json
according to WhatsApp official specification.
"""

import os
import sys
import json
import shutil
import argparse
from pathlib import Path
from PIL import Image, ImageSequence

ASSETS_DIR = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets"
CONTENTS_JSON_PATH = ASSETS_DIR / "contents.json"

DEFAULT_PUBLISHER = "sinanonym"
DEFAULT_EMOJIS = ["😃"]
STICKER_SIZE = (512, 512)
TRAY_SIZE = (96, 96)
MAX_STATIC_KB = 100
MAX_ANIMATED_KB = 500
MAX_TRAY_KB = 50
MIN_STICKERS = 3
MAX_STICKERS = 30
MAX_PACKS = 10


def get_pack_directories():
    """Returns list of potential sticker pack directories in assets."""
    if not ASSETS_DIR.exists():
        return []
    packs = []
    for entry in sorted(ASSETS_DIR.iterdir()):
        if entry.is_dir() and entry.name != "1" and not entry.name.startswith("."):
            packs.append(entry)
    return packs


def human_size(bytes_val):
    """Format bytes into readable string."""
    kb = bytes_val / 1024
    return f"{kb:.1f} KB"


def is_animated_image(image_path: Path) -> bool:
    """Checks whether an image (WebP or GIF) has multiple frames."""
    try:
        with Image.open(image_path) as img:
            return getattr(img, "is_animated", False) and getattr(img, "n_frames", 1) > 1
    except Exception:
        return False


def optimize_image_for_sticker(image_path: Path, output_path: Path = None):
    """
    Resizes static image to exactly 512x512 while maintaining aspect ratio (transparent padding)
    and compresses to WebP <= 100 KB.
    """
    if output_path is None:
        output_path = image_path.with_suffix(".webp")

    with Image.open(image_path) as img:
        img = img.convert("RGBA")
        w, h = img.size

        # Fit within 512x512 with transparent background
        ratio = min(STICKER_SIZE[0] / w, STICKER_SIZE[1] / h)
        new_w = max(1, int(w * ratio))
        new_h = max(1, int(h * ratio))

        resized = img.resize((new_w, new_h), Image.Resampling.LANCZOS)
        canvas = Image.new("RGBA", STICKER_SIZE, (0, 0, 0, 0))
        offset_x = (STICKER_SIZE[0] - new_w) // 2
        offset_y = (STICKER_SIZE[1] - new_h) // 2
        canvas.paste(resized, (offset_x, offset_y), resized)

        # Iterative compression to satisfy <= 100 KB limit
        quality = 90
        while quality >= 20:
            canvas.save(output_path, "WEBP", quality=quality, method=6)
            if output_path.stat().st_size <= MAX_STATIC_KB * 1024:
                break
            quality -= 10

    # If source was not .webp, remove source so only .webp remains in assets
    if image_path != output_path and image_path.exists():
        image_path.unlink()


def optimize_gif_for_sticker(gif_path: Path, output_webp_path: Path = None):
    """
    Converts and resizes animated GIF (or animated WebP) to animated WebP exactly 512x512.
    Ensures:
      - Dimensions are 512x512 with transparent background.
      - Frame durations >= 8ms (WhatsApp requirement).
      - Total duration <= 10,000ms (10 seconds).
      - File size <= 500KB (WhatsApp animated limit).
    """
    if output_webp_path is None:
        output_webp_path = gif_path.with_suffix(".webp")

    with Image.open(gif_path) as im:
        frames = []
        durations = []
        total_duration = 0

        for frame in ImageSequence.Iterator(im):
            duration = frame.info.get("duration", 100)
            if duration < 8:
                duration = 8  # WhatsApp minimum frame duration
            if total_duration + duration > 10000:
                # WhatsApp max total duration is 10s
                break
            total_duration += duration
            durations.append(duration)

            f_rgba = frame.convert("RGBA")
            w, h = f_rgba.size
            ratio = min(STICKER_SIZE[0] / w, STICKER_SIZE[1] / h)
            new_w = max(1, int(w * ratio))
            new_h = max(1, int(h * ratio))

            resized = f_rgba.resize((new_w, new_h), Image.Resampling.LANCZOS)
            canvas = Image.new("RGBA", STICKER_SIZE, (0, 0, 0, 0))
            offset_x = (STICKER_SIZE[0] - new_w) // 2
            offset_y = (STICKER_SIZE[1] - new_h) // 2
            canvas.paste(resized, (offset_x, offset_y), resized)
            frames.append(canvas)

        if not frames:
            raise ValueError(f"No valid frames found in {gif_path}")

        # Compression loop to satisfy <= 500 KB limit
        quality = 85
        while quality >= 20:
            frames[0].save(
                output_webp_path,
                format="WEBP",
                save_all=True,
                append_images=frames[1:],
                duration=durations,
                loop=0,
                quality=quality,
                method=6
            )
            if output_webp_path.stat().st_size <= MAX_ANIMATED_KB * 1024:
                break
            quality -= 15

        # If still exceeds 500KB, downsample frames (skip every second frame)
        if output_webp_path.stat().st_size > MAX_ANIMATED_KB * 1024 and len(frames) > 8:
            reduced_frames = frames[::2]
            reduced_durations = [d * 2 for d in durations[::2]]
            reduced_frames[0].save(
                output_webp_path,
                format="WEBP",
                save_all=True,
                append_images=reduced_frames[1:],
                duration=reduced_durations,
                loop=0,
                quality=30,
                method=6
            )

    # Delete source .gif if converted to .webp
    if gif_path != output_webp_path and gif_path.exists():
        gif_path.unlink()


def generate_tray_icon(source_image_path: Path, output_tray_path: Path):
    """Generates a static 96x96 tray icon PNG <= 50 KB from first frame of sticker."""
    with Image.open(source_image_path) as img:
        # If animated, pick first frame
        if getattr(img, "is_animated", False):
            img.seek(0)
        img = img.convert("RGBA")
        w, h = img.size
        ratio = min(TRAY_SIZE[0] / w, TRAY_SIZE[1] / h)
        new_w = max(1, int(w * ratio))
        new_h = max(1, int(h * ratio))

        resized = img.resize((new_w, new_h), Image.Resampling.LANCZOS)
        canvas = Image.new("RGBA", TRAY_SIZE, (0, 0, 0, 0))
        offset_x = (TRAY_SIZE[0] - new_w) // 2
        offset_y = (TRAY_SIZE[1] - new_h) // 2
        canvas.paste(resized, (offset_x, offset_y), resized)
        canvas.save(output_tray_path, "PNG", optimize=True)


def load_pack_metadata(pack_dir: Path):
    """
    Loads metadata from optional pack_info.json or pack.json in the folder,
    or falls back to clean sensible defaults. Automatically detects animated stickers.
    """
    metadata_file = None
    for name in ["pack.json", "pack_info.json"]:
        cand = pack_dir / name
        if cand.exists():
            metadata_file = cand
            break

    data = {}
    if metadata_file:
        try:
            with open(metadata_file, "r", encoding="utf-8") as f:
                data = json.load(f)
        except Exception as e:
            print(f"  [!] Warning loading {metadata_file.name}: {e}")

    # Defaults
    pack_name = data.get("name")
    if not pack_name:
        import re
        pack_name = re.sub(r"([a-z])([A-Z])", r"\1 \2", pack_dir.name)

    publisher = data.get("publisher", DEFAULT_PUBLISHER)
    tray_image = data.get("tray_image_file", "tray.png")
    image_data_version = data.get("image_data_version", "1")
    avoid_cache = data.get("avoid_cache", False)

    # Check if pack contains animated stickers
    stickers = get_sticker_files_in_pack(pack_dir, tray_image)
    has_animated = any(is_animated_image(st) for st in stickers)
    animated = data.get("animated_sticker_pack", has_animated)

    stickers_config = data.get("stickers", {})

    return {
        "identifier": pack_dir.name,
        "name": pack_name,
        "publisher": publisher,
        "tray_image_file": tray_image,
        "image_data_version": image_data_version,
        "avoid_cache": avoid_cache,
        "animated_sticker_pack": animated,
        "publisher_email": data.get("publisher_email", ""),
        "publisher_website": data.get("publisher_website", ""),
        "privacy_policy_website": data.get("privacy_policy_website", ""),
        "license_agreement_website": data.get("license_agreement_website", ""),
        "stickers_config": stickers_config,
    }


def get_sticker_files_in_pack(pack_dir: Path, tray_image_file: str):
    """Gets list of sticker files (.webp, .gif, .png) excluding tray image."""
    files = []
    for f in sorted(pack_dir.iterdir()):
        if f.is_file() and f.suffix.lower() in [".webp", ".gif", ".png"]:
            if f.name != tray_image_file and not f.name.startswith("tray."):
                files.append(f)
    return files


def pad_pack_stickers(pack_dir: Path, target_count: int = MIN_STICKERS):
    """
    Pads sticker pack with duplicates/variations of existing stickers
    so it meets WhatsApp's minimum requirement of 3 stickers without crashing.
    """
    meta = load_pack_metadata(pack_dir)
    stickers = get_sticker_files_in_pack(pack_dir, meta["tray_image_file"])
    if not stickers:
        return

    current_count = len(stickers)
    if current_count >= target_count:
        return

    print(f"  -> Padding pack '{pack_dir.name}' from {current_count} to {target_count} stickers (WA requirement)...")
    idx = 1
    while len(get_sticker_files_in_pack(pack_dir, meta["tray_image_file"])) < target_count:
        src = stickers[(idx - 1) % len(stickers)]
        ext = src.suffix
        stem = src.stem.split("_copy")[0]
        copy_name = f"{stem}_copy{idx}{ext}"
        copy_dest = pack_dir / copy_name
        shutil.copy2(src, copy_dest)
        idx += 1


def cmd_status(args):
    """Audits current sticker packs and compliance with WhatsApp requirements."""
    packs = get_pack_directories()
    print("=" * 80)
    print("  WhatsApp Stickers Audit Status (Static & GIF/Animated)")
    print("=" * 80)

    if not packs:
        print("No sticker packs found in assets.")
        return

    for pack_dir in packs:
        meta = load_pack_metadata(pack_dir)
        stickers = get_sticker_files_in_pack(pack_dir, meta["tray_image_file"])
        tray_path = pack_dir / meta["tray_image_file"]

        pack_type = "ANIMATED" if meta["animated_sticker_pack"] else "STATIC"
        print(f"\n📁 Pack: {pack_dir.name} ('{meta['name']}') [{pack_type}]")
        print(f"   Publisher : {meta['publisher']}")
        print(f"   Stickers  : {len(stickers)} file(s) (WA requirement: 3 - 30)")

        # Tray status
        if tray_path.exists():
            with Image.open(tray_path) as timg:
                t_w, t_h = timg.size
            t_size = tray_path.stat().st_size
            ok_tray = (t_size <= MAX_TRAY_KB * 1024)
            status_t = "✓ PASS" if ok_tray else "✗ FAIL (Too large)"
            print(f"   Tray Icon : {meta['tray_image_file']} [{t_w}x{t_h}, {human_size(t_size)}] -> {status_t}")
        else:
            print(f"   Tray Icon : ✗ MISSING ({meta['tray_image_file']})")

        # Stickers status
        all_stickers_ok = True
        for st in stickers:
            size = st.stat().st_size
            is_anim = is_animated_image(st)
            limit_kb = MAX_ANIMATED_KB if is_anim else MAX_STATIC_KB
            try:
                with Image.open(st) as simg:
                    w, h = simg.size
                ok_dim = (w == 512 and h == 512)
                ok_size = (size <= limit_kb * 1024)
                ok_format = (st.suffix.lower() == ".webp")
                if not (ok_dim and ok_size and ok_format):
                    all_stickers_ok = False
                fail_reasons = []
                if not ok_dim:
                    fail_reasons.append("Not 512x512")
                if not ok_size:
                    fail_reasons.append(f"> {limit_kb}KB")
                if not ok_format:
                    fail_reasons.append("Needs WebP conversion")

                st_flag = "✓ PASS" if (ok_dim and ok_size and ok_format) else f"✗ FAIL ({', '.join(fail_reasons)})"
                anim_badge = " [Animated]" if is_anim else ""
                print(f"     • {st.name}{anim_badge} [{w}x{h}, {human_size(size)}] -> {st_flag}")
            except Exception as e:
                print(f"     • {st.name} [Error reading image: {e}]")
                all_stickers_ok = False

        # Overall pack verdict
        count_ok = (MIN_STICKERS <= len(stickers) <= MAX_STICKERS)
        if count_ok and tray_path.exists() and all_stickers_ok:
            print(f"   Status    : \033[92mREADY FOR WHATSAPP\033[0m")
        else:
            issues = []
            if not count_ok:
                issues.append(f"Needs {MIN_STICKERS - len(stickers)} more sticker(s)" if len(stickers) < MIN_STICKERS else "Exceeds 30 stickers")
            if not tray_path.exists():
                issues.append("Tray icon missing")
            if not all_stickers_ok:
                issues.append("Needs optimization (resize, convert WebP, or compress)")
            print(f"   Status    : \033[93mNOT READY ({', '.join(issues)})\033[0m")

    print("\n" + "=" * 80)


def cmd_optimize(args):
    """Optimizes all sticker images (PNG, WebP, GIF) and generates tray icons."""
    packs = get_pack_directories()
    target_packs = [p for p in packs if args.pack is None or p.name == args.pack]

    if not target_packs:
        print(f"No packs found matching '{args.pack}'.")
        return

    print("🚀 Optimizing stickers (Static & GIF/Animated)...")
    for pack_dir in target_packs:
        meta = load_pack_metadata(pack_dir)
        stickers = get_sticker_files_in_pack(pack_dir, meta["tray_image_file"])

        print(f"\nProcessing pack: {pack_dir.name}...")

        # Process each sticker
        for st in stickers:
            is_gif = (st.suffix.lower() == ".gif")
            is_anim = is_gif or is_animated_image(st)

            if is_anim:
                print(f"  -> Converting & optimizing animated {st.name} to 512x512 WebP (<=500KB)...")
                optimize_gif_for_sticker(st)
            else:
                print(f"  -> Optimizing static {st.name} to 512x512 WebP (<=100KB)...")
                optimize_image_for_sticker(st)

        # Refresh sticker list after format conversion
        stickers = get_sticker_files_in_pack(pack_dir, meta["tray_image_file"])

        # Auto-pad stickers if requested or if flag --pad-min is set
        if args.pad_min and len(stickers) < MIN_STICKERS:
            pad_pack_stickers(pack_dir, MIN_STICKERS)
            stickers = get_sticker_files_in_pack(pack_dir, meta["tray_image_file"])

        # Generate tray icon if missing or forced
        tray_path = pack_dir / meta["tray_image_file"]
        if (not tray_path.exists() or args.force_tray) and stickers:
            print(f"  -> Generating tray icon {tray_path.name} from {stickers[0].name}...")
            generate_tray_icon(stickers[0], tray_path)

        # Ensure pack.json exists for clean modularity
        pack_json_path = pack_dir / "pack.json"
        if not pack_json_path.exists():
            clean_cfg = {
                "name": meta["name"],
                "publisher": meta["publisher"],
                "tray_image_file": meta["tray_image_file"],
                "image_data_version": "1",
                "animated_sticker_pack": is_animated_image(stickers[0]) if stickers else False,
                "stickers": {s.name: {"emojis": DEFAULT_EMOJIS} for s in stickers}
            }
            with open(pack_json_path, "w", encoding="utf-8") as f:
                json.dump(clean_cfg, f, indent=2, ensure_ascii=False)
                f.write("\n")

    print("\n✨ Optimization complete! Run 'status' or 'build' to review.")


def cmd_build(args):
    """Generates contents.json for valid sticker packs."""
    packs = get_pack_directories()
    valid_pack_list = []
    skipped_packs = []

    for pack_dir in packs:
        meta = load_pack_metadata(pack_dir)
        stickers = get_sticker_files_in_pack(pack_dir, meta["tray_image_file"])
        tray_path = pack_dir / meta["tray_image_file"]

        if args.pad_min and len(stickers) < MIN_STICKERS:
            pad_pack_stickers(pack_dir, MIN_STICKERS)
            stickers = get_sticker_files_in_pack(pack_dir, meta["tray_image_file"])

        # Validate sticker count
        if len(stickers) < MIN_STICKERS:
            skipped_packs.append((pack_dir.name, f"Only has {len(stickers)} stickers (minimum is {MIN_STICKERS})"))
            continue
        if len(stickers) > MAX_STICKERS:
            skipped_packs.append((pack_dir.name, f"Has {len(stickers)} stickers (maximum is {MAX_STICKERS})"))
            continue

        if not tray_path.exists():
            skipped_packs.append((pack_dir.name, f"Missing tray icon ({meta['tray_image_file']})"))
            continue

        # Format stickers
        sticker_entries = []
        cfg = meta.get("stickers_config", {})
        for st in stickers:
            file_cfg = cfg.get(st.name, {})
            emojis = file_cfg.get("emojis", DEFAULT_EMOJIS)
            a11y = file_cfg.get("accessibility_text", "")

            entry = {
                "image_file": st.name,
                "emojis": emojis
            }
            if a11y:
                entry["accessibility_text"] = a11y
            sticker_entries.append(entry)

        pack_obj = {
            "identifier": meta["identifier"],
            "name": meta["name"],
            "publisher": meta["publisher"],
            "tray_image_file": meta["tray_image_file"],
            "image_data_version": meta["image_data_version"],
            "avoid_cache": meta["avoid_cache"],
            "publisher_email": meta["publisher_email"],
            "publisher_website": meta["publisher_website"],
            "privacy_policy_website": meta["privacy_policy_website"],
            "license_agreement_website": meta["license_agreement_website"],
            "animated_sticker_pack": meta["animated_sticker_pack"],
            "stickers": sticker_entries
        }
        valid_pack_list.append(pack_obj)

    # Read existing top-level store links if available
    top_android_link = ""
    top_ios_link = ""
    if CONTENTS_JSON_PATH.exists():
        try:
            with open(CONTENTS_JSON_PATH, "r", encoding="utf-8") as f:
                old = json.load(f)
                top_android_link = old.get("android_play_store_link", "")
                top_ios_link = old.get("ios_app_store_link", "")
        except Exception:
            pass

    contents_obj = {
        "android_play_store_link": top_android_link,
        "ios_app_store_link": top_ios_link,
        "sticker_packs": valid_pack_list
    }

    with open(CONTENTS_JSON_PATH, "w", encoding="utf-8") as f:
        json.dump(contents_obj, f, indent=2, ensure_ascii=False)
        f.write("\n")

    print(f"\n🎉 Successfully updated {CONTENTS_JSON_PATH.name}!")
    print(f"   Included packs: {len(valid_pack_list)}")
    for p in valid_pack_list:
        anim_str = " (Animated)" if p["animated_sticker_pack"] else ""
        print(f"     ✓ {p['identifier']} ('{p['name']}') - {len(p['stickers'])} stickers{anim_str}")

    if skipped_packs:
        print(f"\n⚠️  Skipped packs (not included to prevent Android app crash):")
        for name, reason in skipped_packs:
            print(f"     • {name}: {reason}")
        print("   Tip: Use '--pad-min' flag to auto-fill stickers to 3, or add more stickers.")


def cmd_validate(args):
    """Strictly validates contents.json against all WhatsApp rules."""
    if not CONTENTS_JSON_PATH.exists():
        print(f"Error: {CONTENTS_JSON_PATH} not found. Run 'build' first.")
        sys.exit(1)

    with open(CONTENTS_JSON_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    packs = data.get("sticker_packs", [])
    if not packs:
        print("Error: No sticker packs found in contents.json.")
        sys.exit(1)

    if len(packs) > MAX_PACKS:
        print(f"Error: Maximum allowed sticker packs is {MAX_PACKS}, found {len(packs)}.")
        sys.exit(1)

    errors = []
    for pack in packs:
        ident = pack.get("identifier", "")
        stickers = pack.get("stickers", [])
        tray_file = pack.get("tray_image_file", "")
        is_anim = pack.get("animated_sticker_pack", False)

        pack_folder = ASSETS_DIR / ident
        if not pack_folder.is_dir():
            errors.append(f"Pack '{ident}': Directory {pack_folder} does not exist")
            continue

        if len(stickers) < MIN_STICKERS or len(stickers) > MAX_STICKERS:
            errors.append(f"Pack '{ident}': Sticker count must be between {MIN_STICKERS} and {MAX_STICKERS}, got {len(stickers)}")

        tray_path = pack_folder / tray_file
        if not tray_path.exists():
            errors.append(f"Pack '{ident}': Tray icon '{tray_file}' missing")
        else:
            try:
                with Image.open(tray_path) as timg:
                    tw, th = timg.size
                tsize = tray_path.stat().st_size
                if tsize > MAX_TRAY_KB * 1024:
                    errors.append(f"Pack '{ident}': Tray icon exceeds {MAX_TRAY_KB}KB ({human_size(tsize)})")
                if tw < 24 or tw > 512 or th < 24 or th > 512:
                    errors.append(f"Pack '{ident}': Tray icon dimensions must be between 24 and 512, got {tw}x{th}")
            except Exception as e:
                errors.append(f"Pack '{ident}': Tray icon corrupt ({e})")

        for s in stickers:
            fname = s.get("image_file", "")
            s_path = pack_folder / fname
            if not s_path.exists():
                errors.append(f"Pack '{ident}': Sticker '{fname}' not found on disk")
                continue

            emojis = s.get("emojis", [])
            if not (1 <= len(emojis) <= 3):
                errors.append(f"Pack '{ident}', sticker '{fname}': Emojis count must be 1 to 3, got {len(emojis)}")

            try:
                with Image.open(s_path) as simg:
                    sw, sh = simg.size
                ssize = s_path.stat().st_size
                limit_kb = MAX_ANIMATED_KB if is_anim else MAX_STATIC_KB
                if ssize > limit_kb * 1024:
                    errors.append(f"Pack '{ident}', sticker '{fname}': Size exceeds {limit_kb}KB ({human_size(ssize)})")
                if sw != 512 or sh != 512:
                    errors.append(f"Pack '{ident}', sticker '{fname}': Dimensions must be exactly 512x512, got {sw}x{sh}")
            except Exception as e:
                errors.append(f"Pack '{ident}', sticker '{fname}': Invalid image file ({e})")

    if errors:
        print(f"\n❌ Validation failed with {len(errors)} error(s):")
        for err in errors:
            print(f"  • {err}")
        sys.exit(1)
    else:
        print(f"\n✅ All {len(packs)} pack(s) in contents.json PASSED WhatsApp validation!")


def cmd_init_pack(args):
    """Initializes a new sticker pack folder with template pack.json."""
    folder_name = args.name.replace(" ", "")
    target = ASSETS_DIR / folder_name
    target.mkdir(parents=True, exist_ok=True)

    info_path = target / "pack.json"
    if not info_path.exists():
        template = {
            "name": args.name,
            "publisher": DEFAULT_PUBLISHER,
            "tray_image_file": "tray.png",
            "image_data_version": "1",
            "animated_sticker_pack": args.animated,
            "stickers": {
                "example_001.webp": {
                    "emojis": ["😃", "👍"],
                    "accessibility_text": "Sample description"
                }
            }
        }
        with open(info_path, "w", encoding="utf-8") as f:
            json.dump(template, f, indent=2, ensure_ascii=False)
            f.write("\n")

    print(f"Created new pack folder: {target}")
    print(f"Place at least 3 stickers (WebP or GIF) and run:")
    print(f"  python3 tools/manage_stickers.py optimize --pack {folder_name}")
    print(f"  python3 tools/manage_stickers.py build")


def main():
    parser = argparse.ArgumentParser(description="WhatsApp Sticker Pack Manager for Android")
    subparsers = parser.add_subparsers(dest="command", help="Command to execute")

    # status
    p_status = subparsers.add_parser("status", help="Check WhatsApp compliance of all sticker packs")
    p_status.set_defaults(func=cmd_status)

    # optimize
    p_opt = subparsers.add_parser("optimize", help="Resize (512x512), compress, convert GIF, and generate tray icons")
    p_opt.add_argument("--pack", type=str, default=None, help="Specific pack folder to optimize")
    p_opt.add_argument("--force-tray", action="store_true", help="Regenerate tray icon even if it exists")
    p_opt.add_argument("--pad-min", action="store_true", help="Auto-pad stickers to meet min 3 stickers requirement")
    p_opt.set_defaults(func=cmd_optimize)

    # build
    p_build = subparsers.add_parser("build", help="Build and write contents.json from valid packs")
    p_build.add_argument("--pad-min", action="store_true", help="Auto-pad stickers to meet min 3 stickers requirement")
    p_build.set_defaults(func=cmd_build)

    # validate
    p_val = subparsers.add_parser("validate", help="Validate contents.json against all WhatsApp rules")
    p_val.set_defaults(func=cmd_validate)

    # new-pack
    p_new = subparsers.add_parser("new-pack", help="Create a new pack directory with template")
    p_new.add_argument("name", type=str, help="Name of the sticker pack")
    p_new.add_argument("--animated", action="store_true", help="Mark pack as animated")
    p_new.set_defaults(func=cmd_init_pack)

    args = parser.parse_args()
    if not args.command:
        parser.print_help()
        sys.exit(1)

    args.func(args)


if __name__ == "__main__":
    main()
