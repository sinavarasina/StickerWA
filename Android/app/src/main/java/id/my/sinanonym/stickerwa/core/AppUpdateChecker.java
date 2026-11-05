package id.my.sinanonym.stickerwa.core;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import id.my.sinanonym.stickerwa.BuildConfig;

/**
 * Checks GitHub Releases API for StickerWA updates and provides flexible version comparison.
 */
public final class AppUpdateChecker {

    private static final String TAG = "AppUpdateChecker";
    public static final String GITHUB_REPO_URL = "https://github.com/sinavarasina/StickerWA";
    private static final String GITHUB_API_LATEST_RELEASE = "https://api.github.com/repos/sinavarasina/StickerWA/releases/latest";

    public static class UpdateInfo {
        public final boolean hasUpdate;
        public final String latestVersion;
        public final String currentVersion;
        public final String releaseTitle;
        public final String releaseNotes;
        public final String htmlUrl;
        @Nullable
        public final String apkDownloadUrl;

        public UpdateInfo(
                boolean hasUpdate,
                @NonNull String latestVersion,
                @NonNull String currentVersion,
                @NonNull String releaseTitle,
                @NonNull String releaseNotes,
                @NonNull String htmlUrl,
                @Nullable String apkDownloadUrl) {
            this.hasUpdate = hasUpdate;
            this.latestVersion = latestVersion;
            this.currentVersion = currentVersion;
            this.releaseTitle = releaseTitle;
            this.releaseNotes = releaseNotes;
            this.htmlUrl = htmlUrl;
            this.apkDownloadUrl = apkDownloadUrl;
        }
    }

    public interface UpdateCheckCallback {
        void onCheckComplete(@Nullable UpdateInfo updateInfo, @Nullable String errorMessage);
    }

    private AppUpdateChecker() {}

    public static void checkForUpdates(@NonNull Context context, @NonNull UpdateCheckCallback callback) {
        final String currentVersion = BuildConfig.VERSION_NAME;

        AppExecutors.execute(
                () -> {
                    HttpURLConnection conn = null;
                    try {
                        URL url = new URL(GITHUB_API_LATEST_RELEASE);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                        conn.setRequestProperty("User-Agent", "StickerWA-Android/" + currentVersion);
                        conn.setConnectTimeout(8000);
                        conn.setReadTimeout(8000);

                        int responseCode = conn.getResponseCode();
                        if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                            // No release published yet on repo
                            return new UpdateInfo(
                                    false,
                                    currentVersion,
                                    currentVersion,
                                    "",
                                    "",
                                    GITHUB_REPO_URL,
                                    null
                            );
                        }

                        if (responseCode != HttpURLConnection.HTTP_OK) {
                            throw new Exception("HTTP " + responseCode + " " + conn.getResponseMessage());
                        }

                        StringBuilder response = new StringBuilder();
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                response.append(line);
                            }
                        }

                        JSONObject json = new JSONObject(response.toString());
                        String rawTag = json.optString("tag_name", "").trim();
                        String title = json.optString("name", rawTag);
                        String body = json.optString("body", "");
                        String htmlUrl = json.optString("html_url", GITHUB_REPO_URL);

                        String cleanRemoteVersion = cleanVersion(rawTag);
                        String cleanCurrentVersion = cleanVersion(currentVersion);

                        String apkUrl = null;
                        JSONArray assets = json.optJSONArray("assets");
                        if (assets != null) {
                            for (int i = 0; i < assets.length(); i++) {
                                JSONObject asset = assets.getJSONObject(i);
                                String name = asset.optString("name", "").toLowerCase();
                                if (name.endsWith(".apk")) {
                                    apkUrl = asset.optString("browser_download_url", null);
                                    if (name.contains("universal")) {
                                        break; // Prefer universal APK
                                    }
                                }
                            }
                        }

                        boolean isNewer = isVersionNewer(cleanRemoteVersion, cleanCurrentVersion);

                        return new UpdateInfo(
                                isNewer,
                                cleanRemoteVersion,
                                cleanCurrentVersion,
                                title,
                                body,
                                htmlUrl,
                                apkUrl
                        );
                    } finally {
                        if (conn != null) {
                            conn.disconnect();
                        }
                    }
                },
                result -> callback.onCheckComplete(result, null)
        );
    }

    /**
     * Cleans version string (e.g. "v1.2.3" -> "1.2.3").
     */
    @NonNull
    public static String cleanVersion(@Nullable String raw) {
        if (TextUtils.isEmpty(raw)) {
            return "0.0";
        }
        String v = raw.trim();
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        return v.trim();
    }

    /**
     * Compares two semver-like strings flexibly (e.g. "1.1.0" > "1.0", "2.0" > "1.9.5").
     */
    public static boolean isVersionNewer(@NonNull String remoteVer, @NonNull String currentVer) {
        try {
            String[] remoteParts = remoteVer.split("[.-]");
            String[] currentParts = currentVer.split("[.-]");

            int length = Math.max(remoteParts.length, currentParts.length);
            for (int i = 0; i < length; i++) {
                int r = 0;
                if (i < remoteParts.length) {
                    r = parseSegment(remoteParts[i]);
                }
                int c = 0;
                if (i < currentParts.length) {
                    c = parseSegment(currentParts[i]);
                }
                if (r > c) {
                    return true;
                }
                if (r < c) {
                    return false;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to compare versions: " + remoteVer + " vs " + currentVer, e);
        }
        return false;
    }

    private static int parseSegment(String part) {
        if (TextUtils.isEmpty(part)) return 0;
        StringBuilder digits = new StringBuilder();
        for (char ch : part.toCharArray()) {
            if (Character.isDigit(ch)) {
                digits.append(ch);
            } else {
                break;
            }
        }
        if (digits.length() == 0) return 0;
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
