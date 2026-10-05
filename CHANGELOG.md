# Changelog

## 1.0.4 — Modern Chat, Gemini AI, dan soft UI sounds

- Menambahkan pilihan tampilan `Console` dan `Modern Chat` di Terminal MCC.
- Menambahkan bubble chat dengan alignment incoming/outgoing/system/error.
- Menambahkan animasi fade dan slide untuk pesan baru.
- Memindahkan toolbar copy/simpan/bersihkan ke atas kotak console.
- Menambahkan Gemini Assistant sebagai popup dari Dashboard.
- Menyimpan state chat Gemini selama proses aplikasi masih hidup.
- Menambahkan akses AI ke data profil, config, log, file teks, otomasi, notifikasi, dan metadata runtime dalam sandbox.
- Menambahkan tool untuk membuat/edit config, file, script, automation, setting, dan command dengan konfirmasi.
- Memperbaiki parser Unicode, mojibake, ANSI, warna Minecraft, dan token warna hex.
- Menambahkan shortcut command adaptif maksimal lima item.
- Menambahkan soft UI sound pack CC0 dengan 17 cue semantik.
- Menambahkan animasi intro, Dashboard hero, dan launcher logo yang lebih seimbang.
- Menambahkan runtime MCC ke APK final pada `assets/mcc-bundle.zip`.
- Validasi final: build sukses, APK aligned, signature v2/v3 valid, runtime tersedia.

## 1.0.3 — Gemini dan UI dasar

- Integrasi Google Gemini API melalui REST.
- Penyimpanan API key menggunakan Android Keystore.
- Pemilihan model Gemini dari Google AI Studio.
- Tool AI untuk config, file, script, automation, dan command.
- Perbaikan input config, slider, dropdown, switch, dan perubahan state.
- Dashboard hero dengan glow dan status interaktif.

## 1.0.2 — Baseline patched build

- Baseline APK patched dengan runtime MCC.
- Perbaikan awal konfigurasi dan packaging runtime.

## Development history

Repository ini dikumpulkan dari proses debugging dan pengembangan bertahap MCC Droid Android Native. Detail milestone, keputusan desain, dan validasi ada di [DEVELOPMENT_PROGRESS.md](DEVELOPMENT_PROGRESS.md).
