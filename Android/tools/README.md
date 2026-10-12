# WhatsApp Sticker Management Tool (`manage_stickers.py`)

Tool otomatisasi untuk mengelola, mengoptimasi, memvalidasi, dan meng-generate file `contents.json` WhatsApp Sticker secara terstruktur dan scalable.

---

## 🎯 Fitur Utama

1. **Audit & Status WhatsApp Rules (`status`)**:
   - Mendeteksi stiker **Static** maupun **Animated / GIF**.
   - Memeriksa jumlah stiker per pack (harus antara 3 hingga 30 stiker).
   - Memeriksa batas ukuran (Static $\le$ 100 KB, Animated/GIF $\le$ 500 KB, Tray Icon $\le$ 50 KB).
   - Memeriksa resolusi (stiker wajib 512x512, tray icon antara 24 s/d 512).
2. **Support File GIF & Animated WebP**:
   - Mendukung input file animasi `.gif` dan `.webp`.
   - Mengonversi GIF menjadi Animated WebP 512x512 secara otomatis.
   - Mengatur durasi frame sesuai standar WhatsApp ($\ge$ 8ms per frame, durasi total $\le$ 10 detik).
   - Otomatis mengompres file animasi agar $\le$ 500 KB.
   - Otomatis mengekstrak frame pertama untuk `tray.png` (96x96 static).
   - Menandai pack secara otomatis sebagai `"animated_sticker_pack": true`.
3. **Auto-Optimize & Auto-Padding (`optimize` / `build`)**:
   - Me-resize gambar ke 512x512 secara otomatis dengan transparent padding.
   - Fitur `--pad-min`: jika sebuah pack memiliki stiker kurang dari 3, script dapat menduplikasi stiker yang ada sehingga memenuhi syarat minimal WhatsApp tanpa membuat aplikasi crash.
4. **Modular Metadata (`pack.json`)**:
   - Metadata setiap pack disimpan modular di dalam foldernya masing-masing (`assets/<PackName>/pack.json`).
   - Tidak perlu mengedit ratusan baris di `contents.json` secara manual.
5. **Auto-Build & Strict Validator (`build` & `validate`)**:
   - Meng-compile seluruh pack yang valid ke `contents.json`.
   - Menjalankan uji validasi ketat sesuai `StickerPackValidator.java` sebelum build APK.

---

## 🚀 Cara Penggunaan

Jalankan perintah dari folder `Android/`:

### 1. Periksa Status Stiker
```bash
python3 tools/manage_stickers.py status
```

### 2. Optimasi Gambar, Konversi GIF & Padding
Optimasi semua pack (termasuk auto-convert GIF ke Animated WebP dan auto-pad stiker):
```bash
python3 tools/manage_stickers.py optimize --pad-min
```
Atau hanya untuk pack tertentu:
```bash
python3 tools/manage_stickers.py optimize --pack HackerSeries
```

### 3. Generate / Update `contents.json`
```bash
python3 tools/manage_stickers.py build
```

### 4. Validasi Sebelum Build APK
```bash
python3 tools/manage_stickers.py validate
```

### 5. Buat Pack Baru (Static atau Animated)
```bash
# Static pack
python3 tools/manage_stickers.py new-pack "MemeLucu"

# Animated/GIF pack
python3 tools/manage_stickers.py new-pack "StikerGerak" --animated
```

---

## 📁 Format Modular `pack.json` (Opsional per Folder)

Letakkan file `pack.json` di dalam folder pack (misal: `app/src/main/assets/HackerSeries/pack.json`):

```json
{
  "name": "Hacker Series",
  "publisher": "sinanonym",
  "tray_image_file": "tray.png",
  "image_data_version": "1",
  "animated_sticker_pack": false,
  "stickers": {
    "001_memet_hacker.webp": {
      "emojis": ["💻", "🕶️"],
      "accessibility_text": "Memet hacker with laptop"
    }
  }
}
```
*Catatan: Jika `pack.json` tidak dibuat, script akan otomatis menggunakan nama folder sebagai nama pack dan emoji default `["😃"]`.*
