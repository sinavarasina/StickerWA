package id.my.sinanonym.stickerwa.core;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import id.my.sinanonym.stickerwa.R;
import id.my.sinanonym.stickerwa.data.UserStickerPackManager;
import id.my.sinanonym.stickerwa.data.model.Sticker;
import id.my.sinanonym.stickerwa.data.model.StickerPack;

/**
 * Packages a custom sticker pack into a git-compatible patch bundle (.zip),
 * and generates pre-filled email intents to submit the pack to the repository author.
 */
public final class StickerPackExporter {

    private static final String TAG = "StickerPackExporter";
    public static final String AUTHOR_EMAIL = "sina@sinanonym.my.id";

    private StickerPackExporter() {}

    /**
     * Deletes temporary submission zip files from cache directory to keep storage clean.
     */
    public static void cleanSubmissionCache(@NonNull Context context) {
        try {
            File cacheDir = new File(context.getCacheDir(), "submissions");
            if (cacheDir.exists() && cacheDir.isDirectory()) {
                File[] files = cacheDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        file.delete();
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to clean submission cache", e);
        }
    }

    @NonNull
    public static File createSubmissionZip(@NonNull Context context, @NonNull StickerPack pack) throws IOException {
        cleanSubmissionCache(context);
        File cacheDir = new File(context.getCacheDir(), "submissions");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        File zipFile = new File(cacheDir, "submission_" + pack.identifier + ".zip");
        if (zipFile.exists()) {
            zipFile.delete();
        }

        File packDir = UserStickerPackManager.getCustomPackDir(context, pack.identifier);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            // 1. Write sticker and tray image assets
            File trayFile = new File(packDir, pack.trayImageFile);
            if (trayFile.exists()) {
                addFileToZip(zos, trayFile, "Android/app/src/main/assets/" + pack.identifier + "/" + pack.trayImageFile);
            }

            if (pack.getStickers() != null) {
                for (Sticker sticker : pack.getStickers()) {
                    File stickerFile = new File(packDir, sticker.imageFileName);
                    if (stickerFile.exists()) {
                        addFileToZip(zos, stickerFile, "Android/app/src/main/assets/" + pack.identifier + "/" + sticker.imageFileName);
                    }
                }
            }

            // 2. Write contents_entry.json
            String entryJson = generateManifestEntry(pack);
            addStringToZip(zos, entryJson, "contents_entry.json");

            // 3. Write git format-patch file
            String patchContent = generateGitPatch(pack, entryJson);
            addStringToZip(zos, patchContent, "pack.patch");
        }

        return zipFile;
    }

    @NonNull
    public static File createBatchSubmissionZip(@NonNull Context context, @NonNull List<StickerPack> packs) throws IOException {
        cleanSubmissionCache(context);
        File cacheDir = new File(context.getCacheDir(), "submissions");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        File zipFile = new File(cacheDir, "submission_bundle_" + System.currentTimeMillis() + ".zip");
        if (zipFile.exists()) {
            zipFile.delete();
        }

        JSONArray allManifests = new JSONArray();
        StringBuilder combinedPatches = new StringBuilder();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for (StickerPack pack : packs) {
                File packDir = UserStickerPackManager.getCustomPackDir(context, pack.identifier);

                File trayFile = new File(packDir, pack.trayImageFile);
                if (trayFile.exists()) {
                    addFileToZip(zos, trayFile, "Android/app/src/main/assets/" + pack.identifier + "/" + pack.trayImageFile);
                }

                if (pack.getStickers() != null) {
                    for (Sticker sticker : pack.getStickers()) {
                        File stickerFile = new File(packDir, sticker.imageFileName);
                        if (stickerFile.exists()) {
                            addFileToZip(zos, stickerFile, "Android/app/src/main/assets/" + pack.identifier + "/" + sticker.imageFileName);
                        }
                    }
                }

                String entryJson = generateManifestEntry(pack);
                try {
                    allManifests.put(new JSONObject(entryJson));
                } catch (JSONException ignored) {}

                addStringToZip(zos, entryJson, "manifest_entries/" + pack.identifier + ".json");

                String patchContent = generateGitPatch(pack, entryJson);
                addStringToZip(zos, patchContent, "patches/" + pack.identifier + ".patch");
                combinedPatches.append(patchContent).append("\n\n");
            }

            // Write all manifest entries and combined patch
            addStringToZip(zos, allManifests.toString(), "all_manifest_entries.json");
            addStringToZip(zos, combinedPatches.toString(), "all_packs.patch");
        }

        return zipFile;
    }

    public static void launchSubmissionEmail(@NonNull Context context, @NonNull StickerPack pack) {
        AppExecutors.execute(
                () -> createSubmissionZip(context, pack),
                zipFile -> {
                    try {
                        Uri contentUri = FileProvider.getUriForFile(
                                context,
                                context.getPackageName() + ".fileprovider",
                                zipFile
                        );

                        Intent emailIntent = new Intent(Intent.ACTION_SEND);
                        emailIntent.setType("application/zip");
                        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{AUTHOR_EMAIL});
                        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "[StickerWA Submission] feat(stickers): add '" + pack.name + "' (" + pack.publisher + ")");

                        String body = buildEmailBody(pack);
                        emailIntent.putExtra(Intent.EXTRA_TEXT, body);
                        emailIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                        emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        Intent chooser = Intent.createChooser(emailIntent, context.getString(R.string.submit_to_author_title));
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(chooser);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to launch submission email", e);
                        Toast.makeText(context, context.getString(R.string.submission_error_toast, e.getMessage()), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    public static void launchBatchSubmissionEmail(@NonNull Context context, @NonNull List<StickerPack> packs) {
        if (packs == null || packs.isEmpty()) {
            return;
        }
        if (packs.size() == 1) {
            launchSubmissionEmail(context, packs.get(0));
            return;
        }

        AppExecutors.execute(
                () -> createBatchSubmissionZip(context, packs),
                zipFile -> {
                    try {
                        Uri contentUri = FileProvider.getUriForFile(
                                context,
                                context.getPackageName() + ".fileprovider",
                                zipFile
                        );

                        Intent emailIntent = new Intent(Intent.ACTION_SEND);
                        emailIntent.setType("application/zip");
                        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{AUTHOR_EMAIL});

                        StringBuilder names = new StringBuilder();
                        for (int i = 0; i < packs.size(); i++) {
                            if (i > 0) names.append(", ");
                            names.append(packs.get(i).name);
                        }
                        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "[StickerWA Submission] feat(stickers): add " + packs.size() + " packs (" + names + ")");

                        String body = buildBatchEmailBody(packs);
                        emailIntent.putExtra(Intent.EXTRA_TEXT, body);
                        emailIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                        emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        Intent chooser = Intent.createChooser(emailIntent, context.getString(R.string.batch_submit_title));
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(chooser);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to launch batch submission email", e);
                        Toast.makeText(context, context.getString(R.string.batch_submission_error_toast, e.getMessage()), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private static String buildBatchEmailBody(List<StickerPack> packs) {
        StringBuilder sb = new StringBuilder();
        sb.append("StickerWA Batch Pack Submission\n");
        sb.append("===============================\n\n");
        sb.append("Total Packs Included: ").append(packs.size()).append("\n\n");
        sb.append("Packs Summary:\n");
        for (int i = 0; i < packs.size(); i++) {
            StickerPack pack = packs.get(i);
            int count = pack.getStickers() != null ? pack.getStickers().size() : 0;
            sb.append(i + 1).append(". ").append(pack.name)
              .append(" (Publisher: ").append(pack.publisher)
              .append(", Identifier: ").append(pack.identifier)
              .append(", Stickers: ").append(count);
            if (!TextUtils.isEmpty(pack.publisherEmail)) {
                sb.append(", Email: ").append(pack.publisherEmail);
            }
            sb.append(")\n");
        }
        sb.append("\nRepository: https://github.com/sinavarasina/StickerWA.git\n\n");
        sb.append("Attached package contains:\n");
        sb.append("1. Android/app/src/main/assets/<identifier>/ (All WebP assets)\n");
        sb.append("2. manifest_entries/ (Individual manifest entries)\n");
        sb.append("3. patches/ (Individual git patches)\n");
        sb.append("4. all_manifest_entries.json (Combined JSON array)\n");
        sb.append("5. all_packs.patch (Combined git format-patch)\n\n");
        sb.append("To apply directly:\n");
        sb.append("  git am all_packs.patch\n");
        return sb.toString();
    }

    private static String buildEmailBody(StickerPack pack) {
        int count = pack.getStickers() != null ? pack.getStickers().size() : 0;
        StringBuilder sb = new StringBuilder();
        sb.append("StickerWA Pack Submission\n");
        sb.append("=========================\n\n");
        sb.append("Suggested Git Commit:\n");
        sb.append("  feat(stickers): add '").append(pack.name).append("' sticker pack\n\n");
        sb.append("Pack Details:\n");
        sb.append("- Pack Name: ").append(pack.name).append("\n");
        sb.append("- Identifier: ").append(pack.identifier).append("\n");
        sb.append("- Publisher: ").append(pack.publisher).append("\n");
        if (!TextUtils.isEmpty(pack.publisherEmail)) {
            sb.append("- Author Email: ").append(pack.publisherEmail).append("\n");
        }
        if (!TextUtils.isEmpty(pack.publisherWebsite)) {
            sb.append("- Website: ").append(pack.publisherWebsite).append("\n");
        }
        sb.append("- Total Stickers: ").append(count).append("\n\n");

        sb.append("Repository: https://github.com/sinavarasina/StickerWA.git\n\n");
        sb.append("Attached package contains:\n");
        sb.append("1. Android/app/src/main/assets/").append(pack.identifier).append("/ (WebP assets)\n");
        sb.append("2. contents_entry.json (manifest entry)\n");
        sb.append("3. pack.patch (git format-patch)\n\n");
        sb.append("To apply directly:\n");
        sb.append("  unzip submission_").append(pack.identifier).append(".zip\n");
        sb.append("  git am pack.patch\n");
        sb.append("  (or copy assets/").append(pack.identifier).append(" directly into Android/app/src/main/assets/)\n");
        return sb.toString();
    }

    private static String generateManifestEntry(StickerPack pack) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("identifier", pack.identifier);
            obj.put("name", pack.name);
            obj.put("publisher", pack.publisher);
            obj.put("tray_image_file", pack.trayImageFile);
            obj.put("publisher_email", pack.publisherEmail != null ? pack.publisherEmail : "");
            obj.put("publisher_website", pack.publisherWebsite != null ? pack.publisherWebsite : "");
            obj.put("privacy_policy_website", pack.privacyPolicyWebsite != null ? pack.privacyPolicyWebsite : "");
            obj.put("license_agreement_website", pack.licenseAgreementWebsite != null ? pack.licenseAgreementWebsite : "");
            obj.put("image_data_version", "1");
            obj.put("avoid_cache", false);
            obj.put("animated_sticker_pack", false);

            JSONArray stickers = new JSONArray();
            if (pack.getStickers() != null) {
                for (Sticker sticker : pack.getStickers()) {
                    JSONObject sObj = new JSONObject();
                    sObj.put("image_file_name", sticker.imageFileName);
                    JSONArray emojis = new JSONArray();
                    if (sticker.emojis != null && !sticker.emojis.isEmpty()) {
                        for (String emoji : sticker.emojis) {
                            emojis.put(emoji);
                        }
                    } else {
                        emojis.put("⭐");
                    }
                    sObj.put("emojis", emojis);
                    stickers.put(sObj);
                }
            }
            obj.put("stickers", stickers);
            return obj.toString(2);
        } catch (JSONException e) {
            return "{}";
        }
    }

    private static String generateGitPatch(StickerPack pack, String manifestEntry) {
        String authorName = TextUtils.isEmpty(pack.publisher) ? "Community Contributor" : pack.publisher.trim();
        String authorEmail = TextUtils.isEmpty(pack.publisherEmail) ? "contributor@stickerwa.local" : pack.publisherEmail.trim();
        String dateStr = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US).format(new Date());
        int stickerCount = pack.getStickers() != null ? pack.getStickers().size() : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("From 0000000000000000000000000000000000000000 Mon Sep 17 00:00:00 2001\n");
        sb.append("From: ").append(authorName).append(" <").append(authorEmail).append(">\n");
        sb.append("Date: ").append(dateStr).append("\n");
        sb.append("Subject: [PATCH] feat(stickers): add '").append(pack.name).append("' sticker pack\n\n");
        sb.append("Add custom sticker pack '").append(pack.name).append("' containing ").append(stickerCount).append(" stickers.\n\n");
        sb.append("Pack Details:\n");
        sb.append("- Name: ").append(pack.name).append("\n");
        sb.append("- Identifier: ").append(pack.identifier).append("\n");
        sb.append("- Publisher: ").append(authorName).append("\n");
        if (!TextUtils.isEmpty(pack.publisherEmail)) {
            sb.append("- Contact: ").append(pack.publisherEmail).append("\n");
        }
        if (!TextUtils.isEmpty(pack.publisherWebsite)) {
            sb.append("- Website: ").append(pack.publisherWebsite).append("\n");
        }
        sb.append("- Total Stickers: ").append(stickerCount).append("\n\n");
        sb.append("Signed-off-by: ").append(authorName).append(" <").append(authorEmail).append(">\n");
        sb.append("---\n\n");
        sb.append("# Manifest entry (Android/app/src/main/assets/contents.json):\n");
        sb.append(manifestEntry).append("\n\n");
        sb.append("--\n");
        sb.append("StickerWA Submission System\n");
        return sb.toString();
    }

    private static void addFileToZip(ZipOutputStream zos, File file, String entryPath) throws IOException {
        ZipEntry entry = new ZipEntry(entryPath);
        zos.putNextEntry(entry);
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                zos.write(buffer, 0, read);
            }
        }
        zos.closeEntry();
    }

    private static void addStringToZip(ZipOutputStream zos, String content, String entryPath) throws IOException {
        ZipEntry entry = new ZipEntry(entryPath);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}
