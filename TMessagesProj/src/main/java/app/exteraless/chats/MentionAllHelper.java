package app.exteraless.chats;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * @all и отправка отметок частями (DiveGram).
 *
 * Работает в группах: когда включён переключатель «Упомянуть всех», введённый
 * в поле ввода текст «@all» (без учёта регистра) в момент отправки заменяется
 * на упоминания всех участников группы. Переключатель «Отправлять отметки
 * частями» ограничивает число упоминаний в одном сообщении, чтобы не упереться
 * в лимит длины — остальные уходят следующими сообщениями.
 *
 * Состав участников берётся из полного списка, догруженного через
 * channels.getParticipants: chatFull.participants содержит только первую
 * страницу (обычно 200 человек), и в больших группах @all не доставал до
 * остальных.
 */
public final class MentionAllHelper {

    /** Сколько упоминаний помещается в одно сообщение при включённом split. */
    public static final int MAX_PER_SEND = 10;

    /** Размер страницы channels.getParticipants (сервер больше не отдаёт). */
    private static final int PAGE_SIZE = 200;

    /** Предохранитель от бесконечной догрузки. */
    private static final int MAX_PAGES = 100;

    /** Сколько живёт закэшированный список участников. */
    private static final long CACHE_TTL = 10 * 60 * 1000L;

    private static final HashMap<Long, CacheEntry> cache = new HashMap<>();
    private static final HashMap<Long, ArrayList<Runnable>> waiters = new HashMap<>();

    private MentionAllHelper() {
    }

    /**
     * Если текст — это {@code @all} (возможно, с текстом после), заменяет его
     * упоминаниями участников группы. Возвращает результат, либо null, если
     * замена не понадобилась.
     */
    public static Result apply(long dialogId, CharSequence text, int account) {
        if (!SharedConfig.mentionAllEnabled || TextUtils.isEmpty(text) || dialogId >= 0) {
            return null;
        }
        String raw = text.toString();
        String trimmed = raw.trim();
        // Принимаем как  "@all", так и "@all <текст после>" — тогда подставим
        // упоминания перед этим текстом.
        if (!trimmed.regionMatches(true, 0, "@all", 0, 4)) {
            return null;
        }
        ArrayList<ParticipantInfo> participants = knownParticipants(dialogId, account);
        if (participants == null) {
            return null;
        }
        String prefix = raw.substring(0, raw.indexOf(trimmed));
        String rest = trimmed.substring(4);
        String suffix = rest.startsWith(" ") ? rest : " " + rest;
        return build(account, participants, prefix, suffix, 0);
    }

    /** true, если текст начинается с {@code @all}. */
    public static boolean isMentionAll(CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        String trimmed = text.toString().trim();
        return trimmed.regionMatches(true, 0, "@all", 0, 4);
    }

    /** true, если для диалога уже есть актуальный полный список участников. */
    public static boolean isLoaded(long dialogId) {
        CacheEntry entry = cache.get(dialogId);
        return entry != null && System.currentTimeMillis() - entry.time < CACHE_TTL;
    }

    /**
     * Заранее догружает список участников, если chatFull уже знает, что он
     * неполный. Вызывается при открытии чата — чтобы @all не ждал сеть
     * в момент отправки.
     */
    public static void prime(long dialogId, int account) {
        if (!SharedConfig.mentionAllEnabled || dialogId >= 0 || isLoaded(dialogId)) {
            return;
        }
        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
        if (chat == null || !ChatObject.isMegagroup(chat)) {
            return;
        }
        TLRPC.ChatFull chatFull = MessagesController.getInstance(account).getChatFull(chat.id);
        if (chatFull == null || chatFull.participants == null) {
            // chatFull ещё не загружен — догрузим в момент отправки
            return;
        }
        int known = chatFull.participants.participants == null ? 0 : chatFull.participants.participants.size();
        if (chatFull.participants_count > known) {
            loadAll(dialogId, account, null);
        }
    }

    /**
     * Догружает полный список участников группы. {@code whenDone} вызывается
     * один раз на UI-потоке — как при успехе, так и при ошибке (тогда остаётся
     * то, что уже было в chatFull).
     */
    public static void loadAll(long dialogId, int account, Runnable whenDone) {
        if (dialogId >= 0) {
            if (whenDone != null) {
                AndroidUtilities.runOnUIThread(whenDone);
            }
            return;
        }
        if (isLoaded(dialogId)) {
            if (whenDone != null) {
                AndroidUtilities.runOnUIThread(whenDone);
            }
            return;
        }
        ArrayList<Runnable> pending = waiters.get(dialogId);
        if (pending != null) {
            if (whenDone != null) {
                pending.add(whenDone);
            }
            return;
        }
        pending = new ArrayList<>();
        if (whenDone != null) {
            pending.add(whenDone);
        }
        waiters.put(dialogId, pending);
        fetchPage(dialogId, account, 0, new ArrayList<>(), new HashSet<>(), 0);
    }

    private static void fetchPage(long dialogId, int account, int offset,
                                   ArrayList<ParticipantInfo> collected, HashSet<Long> seen, int page) {
        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
        if (chat == null || !ChatObject.isMegagroup(chat) || page >= MAX_PAGES) {
            finish(dialogId, account, null);
            return;
        }
        TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
        req.channel = MessagesController.getInputChannel(chat);
        req.filter = new TLRPC.TL_channelParticipantsRecent();
        req.offset = offset;
        req.limit = PAGE_SIZE;
        req.hash = 0;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (!(response instanceof TLRPC.TL_channels_channelParticipants)) {
                finish(dialogId, account, null);
                return;
            }
            TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
            MessagesController controller = MessagesController.getInstance(account);
            if (res.users != null) {
                controller.putUsers(res.users, false);
            }
            if (res.chats != null) {
                controller.putChats(res.chats, false);
            }
            int pageSize = 0;
            if (res.participants != null && !res.participants.isEmpty()) {
                pageSize = res.participants.size();
                for (int i = 0; i < pageSize; i++) {
                    TLRPC.ChannelParticipant p = res.participants.get(i);
                    TLRPC.User user = controller.getUser(p.peer != null ? p.peer.user_id : 0);
                    if (user != null && seen.add(user.id)) {
                        collected.add(new ParticipantInfo(user, controller.getInputUser(user)));
                    }
                }
            }
            int total = res.count;
            if (pageSize == 0 || collected.size() >= total) {
                finish(dialogId, account, collected);
            } else {
                fetchPage(dialogId, account, offset + pageSize, collected, seen, page + 1);
            }
        }));
    }

    private static void finish(long dialogId, int account, ArrayList<ParticipantInfo> users) {
        ArrayList<ParticipantInfo> result = filterUsers(users);
        if (result == null) {
            // Не смогли получить полный список — кэшируем то, что есть в chatFull,
            // чтобы не ждать сеть при каждой отправке.
            result = pageFromChatFull(dialogId, account);
        }
        cache.put(dialogId, new CacheEntry(result == null ? new ArrayList<ParticipantInfo>() : result));
        ArrayList<Runnable> pending = waiters.remove(dialogId);
        if (pending != null) {
            for (int i = 0; i < pending.size(); i++) {
                pending.get(i).run();
            }
        }
    }

    /** Полный кэшированный список, либо первая страница из chatFull. */
    private static ArrayList<ParticipantInfo> knownParticipants(long dialogId, int account) {
        CacheEntry entry = cache.get(dialogId);
        if (entry != null) {
            return entry.users.isEmpty() ? null : entry.users;
        }
        return pageFromChatFull(dialogId, account);
    }

    /** Участники из уже загруженного chatFull (первая страница, обычно до 200). */
    private static ArrayList<ParticipantInfo> pageFromChatFull(long dialogId, int account) {
        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
        if (chat == null || !ChatObject.isMegagroup(chat)) {
            return null;
        }
        TLRPC.ChatFull chatFull = MessagesController.getInstance(account).getChatFull(chat.id);
        if (chatFull == null || chatFull.participants == null
                || chatFull.participants.participants == null
                || chatFull.participants.participants.isEmpty()) {
            return null;
        }
        ArrayList<ParticipantInfo> users = new ArrayList<>();
        for (int i = 0; i < chatFull.participants.participants.size(); i++) {
            TLRPC.ChatParticipant p = chatFull.participants.participants.get(i);
            TLRPC.User user = MessagesController.getInstance(account).getUser(p.user_id);
            if (user != null) {
                users.add(new ParticipantInfo(user, MessagesController.getInstance(account).getInputUser(user)));
            }
        }
        return filterUsers(users);
    }

    /** Убирает удалённые аккаунты; возвращает null, если никого не осталось. */
    private static ArrayList<ParticipantInfo> filterUsers(ArrayList<ParticipantInfo> users) {
        if (users == null || users.isEmpty()) {
            return null;
        }
        ArrayList<ParticipantInfo> result = new ArrayList<>(users.size());
        for (int i = 0; i < users.size(); i++) {
            ParticipantInfo p = users.get(i);
            if (p != null && p.user != null && !UserObject.isDeleted(p.user)) {
                result.add(p);
            }
        }
        return result.isEmpty() ? null : result;
    }

    /** Следующая порция упоминаний для уже начатой рассылки. */
    public static Result next(long dialogId, int account, int startIndex) {
        if (!SharedConfig.mentionAllEnabled || dialogId >= 0) {
            return null;
        }
        ArrayList<ParticipantInfo> users = knownParticipants(dialogId, account);
        if (users == null || startIndex >= users.size()) {
            return null;
        }
        return build(account, users, "", "", startIndex);
    }

    /** Собирает одну порцию упоминаний: [startIndex, startIndex + limit). */
    private static Result build(int account, ArrayList<ParticipantInfo> participants,
                                String prefix, String suffix, int startIndex) {
        final int limit = SharedConfig.mentionAllSplitEnabled ? MAX_PER_SEND : participants.size();
        final int end = Math.min(participants.size(), startIndex + limit);
        final boolean more = participants.size() > end;

        StringBuilder sb = new StringBuilder(prefix);
        ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        int offset = prefix.length();
        for (int i = startIndex; i < end; i++) {
            ParticipantInfo p = participants.get(i);
            String name = UserObject.getUserName(p.user);
            if (i > startIndex) {
                sb.append(' ');
                offset++;
            }
            sb.append('@').append(name);
            TLRPC.TL_inputMessageEntityMentionName entity = new TLRPC.TL_inputMessageEntityMentionName();
            entity.user_id = p.inputUser;
            if (entity.user_id != null) {
                entity.offset = offset;
                entity.length = name.length() + 1;
                entities.add(entity);
            }
            offset += name.length() + 1;
        }
        sb.append(suffix);
        return new Result(sb.toString(), entities, more, end);
    }

    private static final class CacheEntry {
        final ArrayList<ParticipantInfo> users;
        final long time;

        CacheEntry(ArrayList<ParticipantInfo> users) {
            this.users = users;
            this.time = System.currentTimeMillis();
        }
    }

    private static final class ParticipantInfo {
        final TLRPC.User user;
        final TLRPC.InputUser inputUser;

        ParticipantInfo(TLRPC.User user, TLRPC.InputUser inputUser) {
            this.user = user;
            this.inputUser = inputUser;
        }
    }

    /** Результат замены: новый текст, entities, признак «ещё остались» и следующий индекс. */
    public static final class Result {
        public final CharSequence text;
        public final ArrayList<TLRPC.MessageEntity> entities;
        public final boolean more;
        public final int nextIndex;

        Result(CharSequence text, ArrayList<TLRPC.MessageEntity> entities, boolean more, int nextIndex) {
            this.text = text;
            this.entities = entities;
            this.more = more;
            this.nextIndex = nextIndex;
        }
    }
}
