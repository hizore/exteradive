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
 * Работает в группах: когда включён переключатель «Упомянуть всех», введённый
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
    private static final int MAX_PER_SEND = 10;

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
        String raw = text.toString();
        String trimmed = raw.trim();
        // Принимаем как  "@all", так и "@all <текст после>" — тогда подставим
        // упоминания перед этим текстом.
        String prefix;
        String suffix;
        if (trimmed.regionMatches(true, 0, "@all", 0, 4)) {
            prefix = raw.substring(0, raw.indexOf(trimmed));
            String rest = trimmed.substring(4);
            suffix = rest.startsWith(" ") ? rest : " " + rest;
        } else {
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
        if (participants.isEmpty()) {
            return null;
        }

        int max = SharedConfig.mentionAllSplitEnabled ? MAX_PER_SEND : Integer.MAX_VALUE;
        int end = Math.min(participants.size(), max);
        StringBuilder sb = new StringBuilder(prefix);
        ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        int offset = prefix.length();
        for (int i = 0; i < end; i++) {
            TLRPC.User user = MessagesController.getInstance(account).getUser(participants.get(i).user_id);
            String name = UserObject.getUserName(user);
            if (i > 0) {
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
        return new Result(sb.toString(), entities, participants.size() > end);
    }

    /** Результат замены: новый текст, entities и признак, что ещё остались участники. */
    public static final class Result {
        public final CharSequence text;
        public final ArrayList<TLRPC.MessageEntity> entities;
        public final boolean more;

        Result(CharSequence text, ArrayList<TLRPC.MessageEntity> entities, boolean more) {
            this.text = text;
            this.entities = entities;
            this.more = more;
        }
    }
}
