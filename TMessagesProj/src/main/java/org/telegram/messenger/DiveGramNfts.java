/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.tl.TL_stars;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Общие «летающие» подарки (NFT). Данные лежат в открытом репозитории в том же формате,
 * который использует DiveGram, поэтому видны всем пользователям клиента.
 *
 * Формат nfts.json:
 * {
 *   "users": {
 *     "<userId>": {
 *       "gifts": ["<base64 TL_stars.StarGiftUnique>", ...],
 *       "pinned": [<giftId>, ...]
 *     }
 *   }
 * }
 */
public class DiveGramNfts {

    public static final String CONFIG_URL = "https://raw.githubusercontent.com/sdvgshni/kfkfkdfkkedk/main/nfts.json";
    public static final long CACHE_TTL = 6 * 60 * 60 * 1000L;

    private static final Object lock = new Object();
    private static final HashMap<Long, ArrayList<TL_stars.TL_starGiftUnique>> giftsMap = new HashMap<>();
    private static final HashMap<Long, HashSet<Long>> pinnedMap = new HashMap<>();
    private static final AtomicBoolean fetching = new AtomicBoolean(false);

    private static File cacheDir() {
        return new File(ApplicationLoader.applicationContext.getFilesDir(), "divegram_nfts");
    }

    private static File cacheFile() {
        return new File(cacheDir(), "nfts.json");
    }

    private static String httpGet(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Divegram/1.0");
            conn.setInstanceFollowRedirects(true);
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.close();
            return out.toString("UTF-8");
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readFile(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString("UTF-8");
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    private static void saveFile(String json) {
        try {
            File dir = cacheDir();
            if (!dir.exists()) {
                dir.mkdirs();
            }
            try (FileOutputStream out = new FileOutputStream(cacheFile())) {
                out.write(json.getBytes("UTF-8"));
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private static long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    private static void parse(String json) {
        if (json == null || json.isEmpty()) {
            return;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONObject users = root.optJSONObject("users");
            HashMap<Long, ArrayList<TL_stars.TL_starGiftUnique>> newGifts = new HashMap<>();
            HashMap<Long, HashSet<Long>> newPinned = new HashMap<>();
            if (users != null) {
                Iterator<String> keys = users.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    long userId;
                    try {
                        userId = Long.parseLong(key.trim());
                    } catch (Throwable t) {
                        continue;
                    }
                    JSONObject user = users.optJSONObject(key);
                    if (user == null) {
                        continue;
                    }
                    JSONArray gifts = user.optJSONArray("gifts");
                    if (gifts != null) {
                        ArrayList<TL_stars.TL_starGiftUnique> list = new ArrayList<>();
                        for (int i = 0; i < gifts.length(); i++) {
                            String encoded = gifts.isNull(i) ? null : String.valueOf(gifts.opt(i));
                            if (encoded == null || encoded.isEmpty()) {
                                continue;
                            }
                            TL_stars.TL_starGiftUnique gift = LocalNftGiftsStore.giftFromBase64(encoded);
                            if (gift != null) {
                                list.add(gift);
                            }
                        }
                        if (!list.isEmpty()) {
                            newGifts.put(userId, list);
                        }
                    }
                    JSONArray pinned = user.optJSONArray("pinned");
                    if (pinned != null) {
                        HashSet<Long> set = new HashSet<>();
                        for (int i = 0; i < pinned.length(); i++) {
                            if (pinned.isNull(i)) {
                                continue;
                            }
                            long giftId = asLong(pinned.opt(i));
                            if (giftId != 0) {
                                set.add(giftId);
                            }
                        }
                        if (!set.isEmpty()) {
                            newPinned.put(userId, set);
                        }
                    }
                }
            }
            synchronized (lock) {
                giftsMap.clear();
                giftsMap.putAll(newGifts);
                pinnedMap.clear();
                pinnedMap.putAll(newPinned);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static void fetch() {
        if (!fetching.compareAndSet(false, true)) {
            return;
        }
        new Thread(() -> {
            try {
                File cached = cacheFile();
                boolean fresh = cached.exists() && (System.currentTimeMillis() - cached.lastModified()) < CACHE_TTL;
                if (fresh) {
                    String json = readFile(cached);
                    if (json != null) {
                        parse(json);
                        notifyChanged();
                        return;
                    }
                }
                String json = httpGet(CONFIG_URL);
                if (json != null && !json.isEmpty()) {
                    parse(json);
                    saveFile(json);
                    notifyChanged();
                } else if (cached.exists()) {
                    String fallback = readFile(cached);
                    if (fallback != null) {
                        parse(fallback);
                        notifyChanged();
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            } finally {
                fetching.set(false);
            }
        }, "DiveGramNfts").start();
    }

    public static List<TL_stars.TL_starGiftUnique> getRemoteGifts(long userId) {
        if (!SharedConfig.nftsEnabled) {
            return Collections.emptyList();
        }
        synchronized (lock) {
            ArrayList<TL_stars.TL_starGiftUnique> list = giftsMap.get(userId);
            return list == null ? Collections.emptyList() : new ArrayList<>(list);
        }
    }

    public static List<TL_stars.TL_starGiftUnique> getRemotePinnedGifts(long userId) {
        List<TL_stars.TL_starGiftUnique> out = new ArrayList<>();
        HashSet<Long> pinned;
        synchronized (lock) {
            pinned = pinnedMap.get(userId);
        }
        if (pinned == null || pinned.isEmpty()) {
            return out;
        }
        for (TL_stars.TL_starGiftUnique gift : getRemoteGifts(userId)) {
            if (pinned.contains(gift.id)) {
                out.add(gift);
            }
        }
        return out;
    }

    public static boolean isRemotePinned(long userId, long giftId) {
        synchronized (lock) {
            HashSet<Long> pinned = pinnedMap.get(userId);
            return pinned != null && pinned.contains(giftId);
        }
    }

    public static List<TL_stars.TL_starGiftUnique> getRemoteGiftsForDialog(long dialogId) {
        return getRemoteGifts(dialogId);
    }

    public static boolean hasRemoteGifts(long userId) {
        if (!SharedConfig.nftsEnabled) {
            return false;
        }
        synchronized (lock) {
            return giftsMap.containsKey(userId);
        }
    }

    public static void scheduleUpload() {
    }

    public static void notifyChanged() {
        try {
            NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.DiveGramNftsChanged);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}