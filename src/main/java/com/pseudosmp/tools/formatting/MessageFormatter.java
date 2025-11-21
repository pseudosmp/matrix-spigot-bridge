package com.pseudosmp.tools.formatting;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.StringEscapeUtils;
import org.bukkit.entity.Player;

import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.ChatColor;

public class MessageFormatter {
    private static final Pattern TIME_PLACEHOLDER = Pattern.compile("\\{TIME:([^}]+)\\}");
    private final Logger logger;
    private final boolean canUsePapi;

    public MessageFormatter(Logger logger, boolean canUsePapi) {
        this.logger = logger;
        this.canUsePapi = canUsePapi;
    }

    public String replaceTimePlaceholders(String input) {
        if (input == null || input.isEmpty()) return input;

        Matcher matcher = TIME_PLACEHOLDER.matcher(input);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String pattern = matcher.group(1);
            String replacement;
            try {
                replacement = DateTimeFormatter.ofPattern(pattern).format(ZonedDateTime.now());
            } catch (IllegalArgumentException ex) {
                logger.warning("Invalid time format '" + pattern + "' in message: " + input);
                replacement = matcher.group(0);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public String replacePlaceholderAPI(Player player, String input) {
        if (input == null || !canUsePapi) return input;
        return PlaceholderAPI.setPlaceholders(player, input);
    }

    public String replaceBuiltInPlaceholders(String input, Map<String, String> placeholders) {
        if (input == null || placeholders == null) return input;
        
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public String stripMinecraftColors(String input) {
        if (input == null) return null;
        return ChatColor.stripColor(input);
    }

    public String minecraftToMatrixHTML(String input) {
        if (input == null) return null;
        
        // Escape HTML special characters first
        input = input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
        
        StringBuilder out = new StringBuilder();
        java.util.Stack<String> tagStack = new java.util.Stack<String>();
        char[] chars = input.toCharArray();
        
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '§' && i + 1 < chars.length) {
                char code = Character.toLowerCase(chars[++i]);
                switch (code) {
                    case 'l': // Bold
                        if (!tagStack.contains("strong")) {
                            out.append("<strong>");
                            tagStack.push("strong");
                        }
                        break;
                    case 'o': // Italic
                        if (!tagStack.contains("em")) {
                            out.append("<em>");
                            tagStack.push("em");
                        }
                        break;
                    case 'n': // Underline
                        if (!tagStack.contains("u")) {
                            out.append("<u>");
                            tagStack.push("u");
                        }
                        break;
                    case 'm': // Strikethrough
                        if (!tagStack.contains("del")) {
                            out.append("<del>");
                            tagStack.push("del");
                        }
                        break;
                    case 'k': // Magic/obfuscated -> spoiler
                        if (!tagStack.contains("spoiler")) {
                            out.append("<span data-mx-spoiler>");
                            tagStack.push("spoiler");
                        }
                        break;
                    case 'r': // Reset - close all tags in reverse order
                        while (!tagStack.isEmpty()) {
                            String tag = tagStack.pop();
                            if (tag.equals("spoiler")) {
                                out.append("</span>");
                            } else {
                                out.append("</").append(tag).append(">");
                            }
                        }
                        break;
                    default:
                        // Color codes - ignore in HTML conversion
                        break;
                }
            } else {
                out.append(chars[i]);
            }
        }
        
        // Close any unclosed tags in reverse order
        while (!tagStack.isEmpty()) {
            String tag = tagStack.pop();
            if (tag.equals("spoiler")) {
                out.append("</span>");
            } else {
                out.append("</").append(tag).append(">");
            }
        }
        
        return out.toString();
    }

    public String matrixHTMLToMinecraft(String input) {
        if (input == null) return null;

        // Replace <br> and <br/> with newlines
        input = input.replaceAll("(?i)<br\\s*/?>", "\n");

        // Headings (convert to bold with newline after)
        input = input.replaceAll("(?i)<(h[1-6])(?:\\s+[^>]*)?>(.+?)<\\1>", "§l$2§r\n");

        // Remove <p> tags inside list items before processing lists
        input = input.replaceAll("(?i)<li(?:\\s+[^>]*)?>\\s*<p(?:\\s+[^>]*)?>", "<li>");
        input = input.replaceAll("(?i)</p>\\s*</li>", "</li>");
        
        // Lists - unordered
        input = input.replaceAll("(?i)<li(?:\\s+[^>]*)?>", "• ");
        input = input.replaceAll("(?i)</li>", "\n");
        input = input.replaceAll("(?i)</?ul(?:\\s+[^>]*)?>", "");
        
        // Lists - ordered (simple conversion, no numbering)
        input = input.replaceAll("(?i)</?ol(?:\\s+[^>]*)?>", "");

        // Bold: <b> or <strong>
        input = input.replaceAll("(?i)<(b|strong)(?:\\s+[^>]*)?>(.+?)</\\1>", "§l$2§r");
        // Italic: <i> or <em>
        input = input.replaceAll("(?i)<(i|em)(?:\\s+[^>]*)?>(.+?)</\\1>", "§o$2§r");
        // Underline: <u>
        input = input.replaceAll("(?i)<u(?:\\s+[^>]*)?>(.+?)</u>", "§n$1§r");
        // Strikethrough: <s>, <strike>, <del>
        input = input.replaceAll("(?i)<(s|strike|del)(?:\\s+[^>]*)?>(.+?)</\\1>", "§m$2§r");
        // Spoilers
        input = input.replaceAll("(?i)<span(?:\\s+[^>]*)?data-mx-spoiler(?:=\"[^\"]*\")?(?:\\s+[^>]*)?>(.+?)</span>", "§k$1§r");
        // Hyperlink (show as "text (url)")
        input = input.replaceAll("(?i)<a(?:\\s+[^>]*)?href=\"([^\"]*)\"(?:\\s+[^>]*)?>(.+?)</a>", "$2 ($1)");

        // Remove all other HTML tags (including leftover attributes)
        input = input.replaceAll("(?i)<[^>]+>", "");

        // Unescape HTML entities
        input = StringEscapeUtils.unescapeHtml4(input);
        // Clean up multiple newlines
        input = input.replaceAll("\n{3,}", "\n\n");

        return input;
    }

    public String yamlEscapeToHtml(String input) {
        if (input == null) return null;
        // CRLF to LF
        input = input.replace("\r\n", "\n").replace("\r", "\n");
        // Replace tabs with 4 spaces
        input = input.replace("\t", "    ");
        // Replace newlines with <br>
        input = input.replace("\n", "<br>");
        return input;
    }

    public String stripHtmlTags(String html) {
        if (html == null) return null;
        
        // Remove closing tags: </tagname>
        html = html.replaceAll("(?i)</[a-z][a-z0-9]*>", "");
        // Remove opening tags with or without attributes: <tagname ...>
        html = html.replaceAll("(?i)<[a-z][a-z0-9]*(?:\\s+[^>]*)?>", "");
        // Remove self-closing tags: <br/>, <hr/>
        html = html.replaceAll("(?i)<[a-z][a-z0-9]*(?:\\s+[^>]*)?/>", "");
        
        // Unescape HTML entities
        html = StringEscapeUtils.unescapeHtml4(html);
        
        return html;
    }
}
