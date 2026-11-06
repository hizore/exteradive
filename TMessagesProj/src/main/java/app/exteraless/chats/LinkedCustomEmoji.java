package app.exteraless.chats;

import android.text.TextUtils;

import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


public final class LinkedCustomEmoji {

    /**
     * Префикс ссылки для премиум-эмодзи.
     *
     * Раньше здесь был {@code tg://emoji?id=}, но клиенты DiveGram и DiveLite
     * разбирают только {@code dive://emoji/<document_id>} — с их форматом
     * отправленный эмодзи у пользователей этих клиентов не отображался.
     * Значение должно совпадать с константой в их MessageObject.
     */
    public static final String PREFIX = "dive://emoji/";

    private LinkedCustomEmoji() {
    }

    /**
     * Премиум-эмодзи отправляем ссылкой у всех, кто не premium.
     *
     * Раньше здесь дополнительно требовался включённый NekoConfig.localPremium, и при
     * выключенном флаге эмодзи уходил как обычный - получатель видел стикер вместо
     * эмодзи. У premium-аккаунтов нативный custom_emoji работает у всех клиентов,
     * поэтому их не трогаем.
     */
    public static boolean canSend(int account) {
        return !UserConfig.getInstance(account).isPremium();
    }

    public static boolean isLink(TLRPC.MessageEntity entity) {
        return entity instanceof TLRPC.TL_messageEntityTextUrl && entity.url != null && entity.url.startsWith(PREFIX);
    }

    /**
     * Ссылка на эмодзи в любом из двух форматов: наш text_url и divegram-овский url.
     * Только для разбора входящих сообщений - {@link #isLink} намеренно оставлен
     * узким, потому что на него завязана логика обработки обычных ссылок.
     */
    private static boolean isDiveLink(TLRPC.MessageEntity entity) {
        return entity.url != null && entity.url.startsWith(PREFIX)
                && (entity instanceof TLRPC.TL_messageEntityTextUrl
                    || entity instanceof TLRPC.TL_messageEntityUrl);
    }

    public static TLRPC.TL_messageEntityTextUrl toLink(TLRPC.TL_messageEntityCustomEmoji emoji) {
        TLRPC.TL_messageEntityTextUrl link = new TLRPC.TL_messageEntityTextUrl();
        link.offset = emoji.offset;
        link.length = emoji.length;
        link.url = PREFIX + emoji.document_id;
        return link;
    }

    /**
     * Разворачивает ссылку на эмодзи в настоящий эмодзи.
     *
     * Главное здесь - разбор по самому тексту, а не по сущности от сервера.
     * Схему {@code dive://} Telegram не знает и молча выбрасывает такую сущность,
     * так что до клиента доходит голый текст. Поэтому ищем "dive://emoji/<id>"
     * прямо в теле сообщения и строим CustomEmoji сами, ровно как DiveGram.
     *
     * Разбор по сущности тоже оставлен - так подхватываются старые сообщения,
     * отправленные форматом text_url, где в тексте лежит сам эмодзи.
     */
    public static boolean parse(CharSequence text, List<TLRPC.MessageEntity> entities) {
        if (text == null) {
            return false;
        }
        boolean changed = false;
        if (entities != null && !entities.isEmpty()) {
            for (int i = 0; i < entities.size(); i++) {
                TLRPC.MessageEntity entity = entities.get(i);
                if (!isDiveLink(entity) || entity.offset < 0 || entity.length <= 0
                        || entity.offset + entity.length > text.length()) {
                    continue;
                }
                long documentId;
                try {
                    documentId = Long.parseLong(entity.url.substring(PREFIX.length()));
                } catch (NumberFormatException e) {
                    FileLog.e(e);
                    continue;
                }
                // Для text_url в тексте лежит сам эмодзи, поэтому убеждаемся,
                // что это ровно один эмодзи. Для url в тексте лежит ссылка -
                // там такая проверка бессмысленна.
                if (entity instanceof TLRPC.TL_messageEntityTextUrl) {
                    int[] emojiOnly = new int[1];
                    ArrayList<Emoji.EmojiSpanRange> emojis = Emoji.parseEmojis(
                            text.subSequence(entity.offset, entity.offset + entity.length).toString(), emojiOnly);
                    if (documentId == 0 || emojiOnly[0] <= 0 || emojis.size() != 1) {
                        continue;
                    }
                } else if (documentId == 0) {
                    continue;
                }
                TLRPC.TL_messageEntityCustomEmoji emoji = new TLRPC.TL_messageEntityCustomEmoji();
                emoji.offset = entity.offset;
                emoji.length = entity.length;
                emoji.document_id = documentId;
                emoji.local = true;
                entities.set(i, emoji);
                changed = true;
            }
        }
        return parseFromText(text, entities) || changed;
    }

    /** Ищет dive://emoji/&lt;id&gt; в самом тексте и ставит поверх CustomEmoji. */
    private static boolean parseFromText(CharSequence text, List<TLRPC.MessageEntity> entities) {
        String txt = text.toString();
        int from = 0;
        int idx;
        boolean changed = false;
        while ((idx = txt.indexOf(PREFIX, from)) >= 0) {
            int end = idx + PREFIX.length();
            while (end < txt.length() && Character.isDigit(txt.charAt(end))) {
                end++;
            }
            if (end > idx + PREFIX.length() && !isCoveredByCustomEmoji(entities, idx, end)) {
                long documentId;
                try {
                    documentId = Long.parseLong(txt.substring(idx + PREFIX.length(), end));
                } catch (NumberFormatException e) {
                    from = Math.max(end, idx + 1);
                    continue;
                }
                TLRPC.TL_messageEntityCustomEmoji emoji = new TLRPC.TL_messageEntityCustomEmoji();
                emoji.offset = idx;
                emoji.length = end - idx;
                emoji.document_id = documentId;
                emoji.local = true;
                if (entities == null) {
                    entities = new ArrayList<>();
                }
                entities.add(emoji);
                changed = true;
            }
            from = Math.max(end, idx + 1);
        }
        return changed;
    }

    private static boolean isCoveredByCustomEmoji(List<TLRPC.MessageEntity> entities, int offset, int end) {
        if (entities == null) {
            return false;
        }
        for (TLRPC.MessageEntity entity : entities) {
            if (entity instanceof TLRPC.TL_messageEntityCustomEmoji
                    && entity.offset <= offset && entity.offset + entity.length >= end) {
                return true;
            }
        }
        return false;
    }

    /**
     * То же, что {@link #parse}, но для сообщения целиком.
     *
     * Отдельный метод потому, что у сообщения без сущностей {@code entities}
     * равен null - новый список просто некуда было бы положить. Здесь он
     * создаётся и сразу подвешивается обратно.
     */
    public static void parseMessage(TLRPC.Message message) {
        if (message == null || message.message == null) {
            return;
        }
        ArrayList<TLRPC.MessageEntity> entities = message.entities;
        if (entities == null) {
            entities = new ArrayList<>();
        }
        if (parse(message.message, entities)) {
            message.entities = entities;
            message.flags |= 8;
        }
        // exteradive (DiveGram): отправитель вставляет литерал "dive://emoji/<id>"
        // прямо в текст сообщения. Сканируем текст и превращаем эти литералы
        // в custom emoji entity, иначе получатель видит голый текст ссылки.
        parseTextLiterals(message.message, entities);
    }

    /**
     * Сканирует текст на литералы {@code dive://emoji/<id>} и заменяет их custom emoji entity.
     * Так делает DiveGram/DiveLite: текст содержит ссылку, entity покрывает её целиком.
     */
    public static void parseTextLiterals(CharSequence text, List<TLRPC.MessageEntity> entities) {
        if (text == null || entities == null || text.length() == 0) {
            return;
        }
        String txt = text.toString();
        String prefix = PREFIX;
        int from = 0;
        int idx;
        while ((idx = txt.indexOf(prefix, from)) >= 0) {
            int end = idx + prefix.length();
            while (end < txt.length() && Character.isDigit(txt.charAt(end))) {
                end++;
            }
            if (end <= idx + prefix.length()) {
                from = idx + prefix.length();
                continue;
            }
            if (!isCoveredByCustomEmoji(entities, idx, end)) {
                try {
                    long docId = Long.parseLong(txt.substring(idx + prefix.length(), end));
                    if (docId > 0) {
                        TLRPC.TL_messageEntityCustomEmoji emoji = new TLRPC.TL_messageEntityCustomEmoji();
                        emoji.offset = idx;
                        emoji.length = end - idx;
                        emoji.document_id = docId;
                        entities.add(emoji);
                    }
                } catch (NumberFormatException ignore) {
                }
            }
            from = end;
        }
    }

    /**
     * Парсит quote_text в цитируемом сообщении (MessageReplyHeader).
     * В новых версиях Telegram цитата хранится в MessageReplyHeader, а не в Message.
     */
    public static void parseQuote(TLRPC.MessageReplyHeader replyTo) {
        if (replyTo == null || replyTo.quote_text == null) {
            return;
        }
        ArrayList<TLRPC.MessageEntity> entities = replyTo.quote_entities;
        if (entities == null) {
            entities = new ArrayList<>();
        }
        if (parse(replyTo.quote_text, entities)) {
            replyTo.quote_entities = entities;
        }
    }

    public static ArrayList<TLRPC.MessageEntity> parsedCopy(CharSequence text, List<TLRPC.MessageEntity> entities) {
        if (entities == null) {
            return null;
        }
        ArrayList<TLRPC.MessageEntity> copy = new ArrayList<>(entities);
        parse(text, copy);
        return copy;
    }

    /**
     * Переписывает премиум-эмодзи в теле сообщения на ссылку {@code dive://emoji/<id>}
     * и попутно правит смещения остальных сущностей. Возвращает новый текст;
     * если менять нечего, возвращает исходный объект без аллокаций.
     *
     * Зачем именно так: DiveGram и DiveLite разбирают только {@link TLRPC.TL_messageEntityUrl},
     * причём ожидают, что в тексте лежит литерал ссылки, а не сам эмодзи. Если отдать
     * text_url с эмодзи в тексте, принимающая сторона формат не узнает и нарисует
     * обычный эмодзи или стикер.
     *
     * Сущности после эмодзи сдвигаются в обратном порядке, чтобы замена не сбила
     * ещё не обработанные смещения.
     */
    public static CharSequence replaceTextForSend(int account, long dialogId, CharSequence text,
                                                  List<TLRPC.MessageEntity> entities) {
        if (text == null || entities == null || entities.isEmpty() || !shouldReplace(account, dialogId)) {
            return text;
        }
        HashSet<Long> groupEmoji = groupEmoji(account, dialogId);
        StringBuilder sb = null;
        for (int i = 0; i < entities.size(); i++) {
            TLRPC.MessageEntity entity = entities.get(i);
            if (!(entity instanceof TLRPC.TL_messageEntityCustomEmoji)
                    || entity.offset < 0 || entity.length <= 0) {
                continue;
            }
            // exteradive: смещения проверяются против текущей длины собираемого
            // текста, а не исходной - после первой конвертации текст растёт
            // (эмодзи из 2 символов -> "dive://emoji/<id>" из 21), и сущности
            // после неё уже не помещаются в исходную длину.
            if (entity.offset + entity.length > (sb != null ? sb.length() : text.length())) {
                continue;
            }
            TLRPC.TL_messageEntityCustomEmoji emoji = (TLRPC.TL_messageEntityCustomEmoji) entity;
            if (!needsLink(account, groupEmoji, emoji.document_id, emoji.document)) {
                continue;
            }
            if (sb == null) {
                sb = new StringBuilder(text);
            }
            String link = PREFIX + emoji.document_id;
            int start = entity.offset;
            int end = entity.offset + entity.length;
            sb.replace(start, end, link);
            shiftEntities(entities, i, end, link.length() - entity.length);
            TLRPC.TL_messageEntityUrl url = new TLRPC.TL_messageEntityUrl();
            url.offset = start;
            url.length = link.length();
            url.url = link;
            entities.set(i, url);
        }
        return sb == null ? text : sb;
    }

    /** Сдвигает смещения сущностей после заменённого отрезка на delta. */
    private static void shiftEntities(List<TLRPC.MessageEntity> entities, int doneIndex, int from, int delta) {
        if (delta == 0) {
            return;
        }
        for (int j = 0; j < entities.size(); j++) {
            if (j == doneIndex) {
                continue;
            }
            TLRPC.MessageEntity other = entities.get(j);
            if (other.offset >= from) {
                other.offset += delta;
            }
        }
    }

    public static void replaceForSend(int account, long dialogId, List<TLRPC.MessageEntity> entities) {
        if (entities == null || entities.isEmpty() || !shouldReplace(account, dialogId)) {
            return;
        }
        HashSet<Long> groupEmoji = groupEmoji(account, dialogId);
        for (int i = 0; i < entities.size(); i++) {
            TLRPC.MessageEntity entity = entities.get(i);
            if (!(entity instanceof TLRPC.TL_messageEntityCustomEmoji)) {
                continue;
            }
            TLRPC.TL_messageEntityCustomEmoji emoji = (TLRPC.TL_messageEntityCustomEmoji) entity;
            if (needsLink(account, groupEmoji, emoji.document_id, emoji.document)) {
                entities.set(i, toLink(emoji));
            }
        }
    }

    public static void replaceForSend(int account, long dialogId, TL_iv.RichMessage rich) {
        if (rich == null || rich.blocks == null || rich.blocks.isEmpty() || !shouldReplace(account, dialogId)) {
            return;
        }
        linkBlocks(account, groupEmoji(account, dialogId), rich.blocks);
    }

    public static TL_iv.textCustomEmoji richEmoji(TL_iv.textUrl url) {
        if (url == null || url.url == null || !url.url.startsWith(PREFIX) || !(url.text instanceof TL_iv.textPlain)) {
            return null;
        }
        String alt = ((TL_iv.textPlain) url.text).text;
        if (TextUtils.isEmpty(alt)) {
            return null;
        }
        long documentId;
        try {
            documentId = Long.parseLong(url.url.substring(PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
        int[] emojiOnly = new int[1];
        ArrayList<Emoji.EmojiSpanRange> emojis = Emoji.parseEmojis(alt, emojiOnly);
        if (documentId == 0 || emojiOnly[0] <= 0 || emojis.size() != 1) {
            return null;
        }
        TL_iv.textCustomEmoji emoji = new TL_iv.textCustomEmoji();
        emoji.document_id = documentId;
        emoji.alt = alt;
        return emoji;
    }

    private static boolean shouldReplace(int account, long dialogId) {
        return canSend(account) && dialogId != UserConfig.getInstance(account).getClientUserId();
    }

    private static HashSet<Long> groupEmoji(int account, long dialogId) {
        if (dialogId >= 0) {
            return null;
        }
        TLRPC.ChatFull chatFull = MessagesController.getInstance(account).getChatFull(-dialogId);
        if (chatFull == null || chatFull.emojiset == null) {
            return null;
        }
        TLRPC.TL_messages_stickerSet set = MediaDataController.getInstance(account).getGroupStickerSetById(chatFull.emojiset);
        if (set == null || set.documents == null) {
            return null;
        }
        HashSet<Long> ids = new HashSet<>();
        for (TLRPC.Document document : set.documents) {
            ids.add(document.id);
        }
        return ids;
    }

    private static boolean needsLink(int account, HashSet<Long> groupEmoji, long documentId, TLRPC.Document document) {
        if (groupEmoji != null && groupEmoji.contains(documentId)) {
            return false;
        }
        if (document == null) {
            document = AnimatedEmojiDrawable.findDocument(account, documentId);
        }
        return !MessageObject.isFreeEmoji(document);
    }

    private static void linkBlocks(int account, HashSet<Long> groupEmoji, List<TL_iv.PageBlock> blocks) {
        if (blocks == null) {
            return;
        }
        for (int i = 0; i < blocks.size(); i++) {
            linkBlock(account, groupEmoji, blocks.get(i));
        }
    }

    private static void linkBlock(int account, HashSet<Long> groupEmoji, TL_iv.PageBlock b) {
        if (b == null) {
            return;
        }
        b.text = linkText(account, groupEmoji, b.text);
        if (b.caption != null) {
            b.caption.text = linkText(account, groupEmoji, b.caption.text);
            b.caption.credit = linkText(account, groupEmoji, b.caption.credit);
        }
        if (b instanceof TL_iv.pageBlockBlockquote) {
            TL_iv.pageBlockBlockquote q = (TL_iv.pageBlockBlockquote) b;
            q.caption = linkText(account, groupEmoji, q.caption);
        } else if (b instanceof TL_iv.pageBlockPullquote) {
            TL_iv.pageBlockPullquote q = (TL_iv.pageBlockPullquote) b;
            q.caption = linkText(account, groupEmoji, q.caption);
        } else if (b instanceof TL_iv.pageBlockBlockquoteBlocks) {
            TL_iv.pageBlockBlockquoteBlocks q = (TL_iv.pageBlockBlockquoteBlocks) b;
            q.caption = linkText(account, groupEmoji, q.caption);
            linkBlocks(account, groupEmoji, q.blocks);
        } else if (b instanceof TL_iv.pageBlockDetails) {
            TL_iv.pageBlockDetails d = (TL_iv.pageBlockDetails) b;
            d.title = linkText(account, groupEmoji, d.title);
            linkBlocks(account, groupEmoji, d.blocks);
        } else if (b instanceof TL_iv.pageBlockList) {
            for (TL_iv.PageListItem item : ((TL_iv.pageBlockList) b).items) {
                if (item instanceof TL_iv.TL_pageListItemText) {
                    TL_iv.TL_pageListItemText text = (TL_iv.TL_pageListItemText) item;
                    text.text = linkText(account, groupEmoji, text.text);
                } else if (item instanceof TL_iv.TL_pageListItemBlocks) {
                    linkBlocks(account, groupEmoji, ((TL_iv.TL_pageListItemBlocks) item).blocks);
                }
            }
        } else if (b instanceof TL_iv.pageBlockOrderedList) {
            for (TL_iv.PageListOrderedItem item : ((TL_iv.pageBlockOrderedList) b).items) {
                if (item instanceof TL_iv.TL_pageListOrderedItemText) {
                    TL_iv.TL_pageListOrderedItemText text = (TL_iv.TL_pageListOrderedItemText) item;
                    text.text = linkText(account, groupEmoji, text.text);
                } else if (item instanceof TL_iv.TL_pageListOrderedItemBlocks) {
                    linkBlocks(account, groupEmoji, ((TL_iv.TL_pageListOrderedItemBlocks) item).blocks);
                }
            }
        } else if (b instanceof TL_iv.pageBlockTable) {
            TL_iv.pageBlockTable t = (TL_iv.pageBlockTable) b;
            t.title = linkText(account, groupEmoji, t.title);
            if (t.rows != null) {
                for (TL_iv.pageTableRow row : t.rows) {
                    if (row == null || row.cells == null) {
                        continue;
                    }
                    for (TL_iv.pageTableCell cell : row.cells) {
                        if (cell != null) {
                            cell.text = linkText(account, groupEmoji, cell.text);
                        }
                    }
                }
            }
        } else if (b instanceof TL_iv.pageBlockButtonRow) {
            TL_iv.pageBlockButtonRow row = (TL_iv.pageBlockButtonRow) b;
            if (row.buttons != null) {
                for (TL_keyboard.PageButton button : row.buttons) {
                    if (button != null) {
                        button.text = linkText(account, groupEmoji, button.text);
                    }
                }
            }
        } else if (b instanceof TL_iv.pageBlockCollage) {
            linkBlocks(account, groupEmoji, ((TL_iv.pageBlockCollage) b).items);
        } else if (b instanceof TL_iv.pageBlockSlideshow) {
            linkBlocks(account, groupEmoji, ((TL_iv.pageBlockSlideshow) b).items);
        }
    }

    private static TL_iv.RichText linkText(int account, HashSet<Long> groupEmoji, TL_iv.RichText rt) {
        if (rt == null) {
            return null;
        }
        if (rt instanceof TL_iv.textCustomEmoji) {
            TL_iv.textCustomEmoji emoji = (TL_iv.textCustomEmoji) rt;
            if (!needsLink(account, groupEmoji, emoji.document_id, null)) {
                return rt;
            }
            TL_iv.textUrl url = new TL_iv.textUrl();
            TL_iv.textPlain plain = new TL_iv.textPlain();
            plain.text = TextUtils.isEmpty(emoji.alt) ? "\uD83D\uDE00" : emoji.alt;
            plain.parentRichText = url;
            url.text = plain;
            url.url = PREFIX + emoji.document_id;
            url.parentRichText = emoji.parentRichText;
            return url;
        }
        if (rt instanceof TL_iv.textConcat) {
            ArrayList<TL_iv.RichText> texts = rt.texts;
            for (int i = 0; i < texts.size(); i++) {
                texts.set(i, linkText(account, groupEmoji, texts.get(i)));
            }
            return rt;
        }
        rt.text = linkText(account, groupEmoji, rt.text);
        return rt;
    }

    public static boolean isLinkOnlyMessage(TLRPC.Message message) {
        if (message == null || message.entities == null || message.entities.isEmpty() || !MessageObject.isMediaEmptyWebpage(message)) {
            return false;
        }
        boolean found = false;
        for (TLRPC.MessageEntity entity : message.entities) {
            if (entity instanceof TLRPC.TL_messageEntityTextUrl) {
                if (!isLink(entity)) {
                    return false;
                }
                found = true;
            } else if (entity instanceof TLRPC.TL_messageEntityUrl || entity instanceof TLRPC.TL_messageEntityEmail) {
                return false;
            } else if (entity instanceof TLRPC.TL_messageEntityCustomEmoji && ((TLRPC.TL_messageEntityCustomEmoji) entity).local) {
                found = true;
            }
        }
        return found;
    }
}
