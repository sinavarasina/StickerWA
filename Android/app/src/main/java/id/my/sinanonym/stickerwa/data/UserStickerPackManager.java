package id.my.sinanonym.stickerwa.data;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import id.my.sinanonym.stickerwa.data.model.Sticker;
import id.my.sinanonym.stickerwa.data.model.StickerPack;

/**
 * Manages locally created custom sticker packs stored in app internal storage.
 */
public final class UserStickerPackManager {

    private static final String TAG = "UserStickerPackManager";
    private static final String CUSTOM_PACKS_DIR = "custom_packs";
    private static final String CUSTOM_PACKS_JSON = "custom_packs.json";

    private UserStickerPackManager() {}

    public static File getCustomPacksRoot(Context context) {
        File dir = new File(context.getFilesDir(), CUSTOM_PACKS_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static File getCustomPackDir(Context context, String identifier) {
        File packDir = new File(getCustomPacksRoot(context), identifier);
        if (!packDir.exists()) {
            packDir.mkdirs();
        }
        return packDir;
    }

    public static File getCustomStickerFile(Context context, String identifier, String fileName) {
        return new File(getCustomPackDir(context, identifier), fileName);
    }

    public static boolean isUserPack(@NonNull Context context, String identifier) {
        if (identifier == null) {
            return false;
        }
        if (identifier.startsWith("custom_")) {
            return true;
        }
        List<StickerPack> userPacks = loadUserPacks(context);
        for (StickerPack pack : userPacks) {
            if (identifier.equals(pack.identifier)) {
                return true;
            }
        }
        return false;
    }

    public static synchronized List<StickerPack> loadUserPacks(@NonNull Context context) {
        List<StickerPack> result = new ArrayList<>();
        File jsonFile = new File(context.getFilesDir(), CUSTOM_PACKS_JSON);
        if (!jsonFile.exists()) {
            return result;
        }

        try (InputStream in = new FileInputStream(jsonFile)) {
            byte[] bytes = new byte[(int) jsonFile.length()];
            int read = in.read(bytes);
            if (read <= 0) {
                return result;
            }
            String jsonStr = new String(bytes, StandardCharsets.UTF_8);
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                StickerPack pack = parsePack(obj);
                if (pack != null) {
                    result.add(pack);
                }
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to load user packs from JSON", e);
        }

        return result;
    }

    public static synchronized void saveUserPack(@NonNull Context context, @NonNull StickerPack newPack) {
        List<StickerPack> current = loadUserPacks(context);
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).identifier.equals(newPack.identifier)) {
                current.remove(i);
                break;
            }
        }
        current.add(newPack);
        persistPacks(context, current);
        StickerRepository.clearCache();
    }

    public static synchronized boolean deleteUserPack(@NonNull Context context, @NonNull String identifier) {
        List<StickerPack> current = loadUserPacks(context);
        boolean removed = false;
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).identifier.equals(identifier)) {
                current.remove(i);
                removed = true;
                break;
            }
        }

        if (removed) {
            persistPacks(context, current);
            // Delete pack directory and files
            File dir = new File(getCustomPacksRoot(context), identifier);
            deleteRecursive(dir);
            StickerRepository.clearCache();
        }
        return removed;
    }

    private static void persistPacks(Context context, List<StickerPack> packs) {
        File jsonFile = new File(context.getFilesDir(), CUSTOM_PACKS_JSON);
        try {
            JSONArray array = new JSONArray();
            for (StickerPack pack : packs) {
                JSONObject obj = serializePack(pack);
                array.put(obj);
            }
            try (FileOutputStream fos = new FileOutputStream(jsonFile)) {
                fos.write(array.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to persist user packs JSON", e);
        }
    }

    private static StickerPack parsePack(JSONObject obj) throws JSONException {
        String identifier = obj.getString("identifier");
        String name = obj.getString("name");
        String publisher = obj.getString("publisher");
        String trayImageFile = obj.getString("tray_image_file");
        String publisherEmail = obj.optString("publisher_email", "");
        String publisherWebsite = obj.optString("publisher_website", "");
        String privacyPolicyWebsite = obj.optString("privacy_policy_website", "");
        String licenseAgreementWebsite = obj.optString("license_agreement_website", "");
        String imageDataVersion = obj.optString("image_data_version", "1");
        boolean avoidCache = obj.optBoolean("avoid_cache", false);
        boolean animatedStickerPack = obj.optBoolean("animated_sticker_pack", false);

        StickerPack pack = new StickerPack(
                identifier,
                name,
                publisher,
                trayImageFile,
                publisherEmail,
                publisherWebsite,
                privacyPolicyWebsite,
                licenseAgreementWebsite,
                imageDataVersion,
                avoidCache,
                animatedStickerPack
        );
        pack.setIsCustom(true);

        JSONArray stickersArray = obj.optJSONArray("stickers");
        List<Sticker> stickers = new ArrayList<>();
        if (stickersArray != null) {
            for (int i = 0; i < stickersArray.length(); i++) {
                JSONObject sObj = stickersArray.getJSONObject(i);
                String fileName = sObj.getString("image_file_name");
                String a11yText = sObj.optString("accessibility_text", "");
                JSONArray emojiArr = sObj.optJSONArray("emojis");
                List<String> emojis = new ArrayList<>();
                if (emojiArr != null) {
                    for (int j = 0; j < emojiArr.length(); j++) {
                        emojis.add(emojiArr.getString(j));
                    }
                }
                if (emojis.isEmpty()) {
                    emojis.add("⭐");
                }
                Sticker sticker = new Sticker(fileName, emojis, a11yText);
                stickers.add(sticker);
            }
        }
        pack.setStickers(stickers);
        return pack;
    }

    private static JSONObject serializePack(StickerPack pack) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("identifier", pack.identifier);
        obj.put("name", pack.name);
        obj.put("publisher", pack.publisher);
        obj.put("tray_image_file", pack.trayImageFile);
        obj.put("publisher_email", pack.publisherEmail);
        obj.put("publisher_website", pack.publisherWebsite);
        obj.put("privacy_policy_website", pack.privacyPolicyWebsite);
        obj.put("license_agreement_website", pack.licenseAgreementWebsite);
        obj.put("image_data_version", pack.imageDataVersion);
        obj.put("avoid_cache", pack.avoidCache);
        obj.put("animated_sticker_pack", pack.animatedStickerPack);

        JSONArray stickersArray = new JSONArray();
        if (pack.getStickers() != null) {
            for (Sticker sticker : pack.getStickers()) {
                JSONObject sObj = new JSONObject();
                sObj.put("image_file_name", sticker.imageFileName);
                sObj.put("accessibility_text", sticker.accessibilityText != null ? sticker.accessibilityText : "");
                JSONArray emojiArr = new JSONArray();
                if (sticker.emojis != null) {
                    for (String emoji : sticker.emojis) {
                        emojiArr.put(emoji);
                    }
                }
                sObj.put("emojis", emojiArr);
                stickersArray.put(sObj);
            }
        }
        obj.put("stickers", stickersArray);
        return obj;
    }

    private static boolean deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) {
            return false;
        }
        if (fileOrDirectory.isDirectory()) {
            File[] files = fileOrDirectory.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        return fileOrDirectory.delete();
    }
}
