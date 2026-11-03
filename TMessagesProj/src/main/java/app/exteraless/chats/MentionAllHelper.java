package app.exteraless.chats;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * @all и отправка отметок частями (DiveGram).
 *
 * Работает в группах: когда включён переключатель «Упоминать всех», введённый
 * в поле ввода текст «@all» (без учёта регистра) в момент отправки заменяется
 * на упоминания всех участников группы. Переключатель «Отправлять отметки
 * частями» ограничивает число упоминаний в одном сообщении, чтобы не упереться
 * в лимит длины — остальные уходят следующими сообщениями.
 *
 * Логика вынесена в отдельный класс: вызывается из ChatActivityEnterView перед
 * сборкой entities, потому что там известен dialogId, а getEntities — статический
 * метод и контекст чата не получает.
 */
public final class MentionAllHelper {

    /** Сколько упоминаний помещается в одно сообщение при включённом split. */
    public static final int MAX_PER_SEND = 10;

    private MentionAllHelper() {
    }

    /**
     * Если текст — это {@code @all} (возможно, с текстом после), заменяет его
     * упоминаниями участников группы. Возвращает результат, либо null, если
     * замена не понадобилась.
     *
     * @param dialogId  диалог, куда отправляется сообщение
     * @param text      исходный текст
     * @param account   аккаунт
     * @return Result с новым текстом и entities, или null
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
        ArrayList<TLRPC.ChatParticipant> participants = loadParticipants(dialogId, account);
        if (participants == null) {
            return null;
        }
        String prefix = raw.substring(0, raw.indexOf(trimmed));
        String rest = trimmed.substring(4);
        String suffix = rest.startsWith(" ") ? rest : " " + rest;
        return build(account, participants, prefix, suffix, 0);
    }

    /**
     * Следующая порция упоминаний для уже начатой рассылки.
     *
     * @param dialogId   диалог рассылки (проверяется снова — состав мог измениться)
     * @param account    аккаунт
     * @param startIndex индекс первого участника этой порции
     * @return Result с текстом и entities, или null, если отправлять больше нечего
     */
    public static Result next(long dialogId, int account, int startIndex) {
        if (!SharedConfig.mentionAllEnabled || dialogId >= 0) {
            return null;
        }
        ArrayList<TLRPC.ChatParticipant> participants = loadParticipants(dialogId, account);
        if (participants == null || startIndex >= participants.size()) {
            return null;
        }
        return build(account, participants, "", "", startIndex);
    }

    /** Проверяет, что диалог — группа, и собирает её участников. */
    private static ArrayList<TLRPC.ChatParticipant> loadParticipants(long dialogId, int account) {
        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
        if (chat == null || !ChatObject.isMegagroup(chat)) {
            // Только группы/супергруппы — в личных чатах @all бессмыслен
            return null;
        }
        TLRPC.ChatFull chatFull = MessagesController.getInstance(account).getChatFull(chat.id);
        if (chatFull == null || chatFull.participants == null
                || chatFull.participants.participants == null
                || chatFull.participants.participants.isEmpty()) {
            return null;
        }
        ArrayList<TLRPC.ChatParticipant> participants = new ArrayList<>();
        for (int i = 0; i < chatFull.participants.participants.size(); i++) {
            TLRPC.ChatParticipant p = chatFull.participants.participants.get(i);
            TLRPC.User user = MessagesController.getInstance(account).getUser(p.user_id);
            if (user != null && !UserObject.isDeleted(user)) {
                participants.add(p);
            }
        }
        return participants.isEmpty() ? null : participants;
    }

    /** Собирает одну порцию упоминаний: [startIndex, startIndex + limit). */
    private static Result build(int account, ArrayList<TLRPC.ChatParticipant> participants,
                                String prefix, String suffix, int startIndex) {
        final int limit = SharedConfig.mentionAllSplitEnabled ? MAX_PER_SEND : participants.size();
        final int end = Math.min(participants.size(), startIndex + limit);
        final boolean more = participants.size() > end;

        StringBuilder sb = new StringBuilder(prefix);
        ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        int offset = prefix.length();
        for (int i = startIndex; i < end; i++) {
            TLRPC.User user = MessagesController.getInstance(account).getUser(participants.get(i).user_id);
            String name = UserObject.getUserName(user);
            if (i > startIndex) {
                sb.append(' ');
                offset++;
            }
            sb.append('@').append(name);
            TLRPC.TL_inputMessageEntityMentionName entity = new TLRPC.TL_inputMessageEntityMentionName();
            entity.user_id = MessagesController.getInstance(account).getInputUser(user);
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
