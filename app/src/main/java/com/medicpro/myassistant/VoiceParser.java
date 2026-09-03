package com.medicpro.myassistant;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VoiceParser {
    public enum Intent { TRANSACTION, TASK, QUERY, UNKNOWN }

    public static class ParsedCommand {
        public Intent intent = Intent.UNKNOWN;
        public String raw = "";
        public String direction = "";
        public double amount = 0;
        public String person = "";
        public String mode = "Other";
        public String business = "General";
        public String taskTitle = "";
        public LocalDateTime dueAt;
        public String queryType = "";
    }

    private static final Map<String, Double> NUM = new HashMap<>();
    static {
        String[][] pairs = {
            {"zero","0"},{"शून्य","0"},{"ek","1"},{"एक","1"},{"do","2"},{"दो","2"},{"teen","3"},{"तीन","3"},
            {"char","4"},{"chaar","4"},{"चार","4"},{"panch","5"},{"paanch","5"},{"पांच","5"},{"पाँच","5"},
            {"chhe","6"},{"che","6"},{"छह","6"},{"saat","7"},{"सात","7"},{"aath","8"},{"आठ","8"},{"nau","9"},{"नौ","9"},
            {"das","10"},{"दस","10"},{"gyarah","11"},{"ग्यारह","11"},{"barah","12"},{"बारह","12"},{"terah","13"},{"तेरह","13"},
            {"chaudah","14"},{"चौदह","14"},{"pandrah","15"},{"पंद्रह","15"},{"solah","16"},{"सोलह","16"},{"satrah","17"},{"सत्रह","17"},
            {"atharah","18"},{"अठारह","18"},{"unnis","19"},{"उन्नीस","19"},{"bees","20"},{"बीस","20"},{"tees","30"},{"तीस","30"},
            {"chalis","40"},{"चालीस","40"},{"pachas","50"},{"पचास","50"},{"saath","60"},{"साठ","60"},{"sattar","70"},{"सत्तर","70"},
            {"assi","80"},{"अस्सी","80"},{"nabbe","90"},{"नब्बे","90"},{"sau","100"},{"hundred","100"},{"सौ","100"},
            {"dhai","2.5"},{"ढाई","2.5"}
        };
        for (String[] p : pairs) NUM.put(p[0], Double.parseDouble(p[1]));
    }

    public static ParsedCommand parse(String text, String[] businesses) {
        ParsedCommand p = new ParsedCommand();
        p.raw = text == null ? "" : text.trim();
        String s = normalize(p.raw);
        p.business = detectBusiness(s, businesses);

        if (isQuery(s)) {
            p.intent = Intent.QUERY;
            p.person = extractPerson(s);
            if (containsAny(s, "आज के काम", "aaj ke kaam", "आज क्या काम", "today task", "क्या काम")) p.queryType = "TODAY_TASKS";
            else if (!p.person.isEmpty() && containsAny(s, "हिसाब", "hisab", "account")) p.queryType = "PERSON";
            else if (containsAny(s, "लेना", "lene", "receivable", "आना है")) p.queryType = "DUE_IN";
            else if (containsAny(s, "देना", "dene", "payable", "जाना है")) p.queryType = "DUE_OUT";
            else if (containsAny(s, "खर्च", "expense", "दिया", "diya", "paid")) p.queryType = "TODAY_OUT";
            else p.queryType = "TODAY_SUMMARY";
            return p;
        }

        if (isTask(s)) {
            p.intent = Intent.TASK;
            p.taskTitle = cleanTaskTitle(p.raw);
            p.dueAt = parseDateTime(s);
            return p;
        }

        String direction = detectDirection(s);
        double amount = extractAmount(s);
        if (!direction.isEmpty() && amount > 0) {
            p.intent = Intent.TRANSACTION;
            p.direction = direction;
            p.amount = amount;
            p.person = extractPerson(s);
            p.mode = detectMode(s);
            return p;
        }
        return p;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replace(',', ' ').replace('₹',' ').replaceAll("\\s+", " ").trim();
    }

    private static boolean isQuery(String s) {
        return containsAny(s, "बताओ", "batao", "कितना", "kitna", "हिसाब", "hisab", "आज के काम", "क्या काम", "summary", "balance");
    }

    private static boolean isTask(String s) {
        return containsAny(s, "याद दिलाना", "याद दिला", "remind", "करना है", "karna hai", "मिलना है", "milna hai", "काम है", "task")
                && !containsAny(s, "देना है", "dena hai", "लेना है", "lena hai");
    }

    private static String detectDirection(String s) {
        if (containsAny(s, "लेना है", "lene hain", "lena hai", "आना है", "receivable")) return "DUE_IN";
        if (containsAny(s, "देना है", "dene hain", "dena hai", "payable")) return "DUE_OUT";
        if (containsAny(s, "मिले", "मिला", "लिया", "लिए", "received", "receive", "आए", "आया", "credit")) return "IN";
        if (containsAny(s, "दिया", "दिए", "भेजा", "भेजे", "paid", "pay kiya", "खर्च", "expense", "debit")) return "OUT";
        return "";
    }

    private static String detectMode(String s) {
        if (containsAny(s, "cash", "कैश", "नकद")) return "Cash";
        if (containsAny(s, "upi", "phonepe", "फोनपे", "gpay", "google pay", "paytm", "पेटीएम")) return "UPI";
        if (containsAny(s, "bank", "बैंक", "neft", "imps", "rtgs")) return "Bank";
        return "Other";
    }

    private static String detectBusiness(String s, String[] businesses) {
        if (businesses != null) {
            for (String b : businesses) {
                if (b != null && !b.trim().isEmpty() && s.contains(b.toLowerCase(Locale.ROOT))) return b;
            }
        }
        return "General";
    }

    public static double extractAmount(String s) {
        Matcher m = Pattern.compile("(?<!\\d)(\\d+(?:\\.\\d+)?)(?!\\d)").matcher(s);
        double best = 0;
        while (m.find()) {
            try {
                double n = Double.parseDouble(m.group(1));
                if (n > best) best = n;
            } catch (Exception ignored) {}
        }
        if (best > 0) return best;

        String[] t = s.split(" ");
        double total = 0, current = 0;
        boolean found = false;
        for (String raw : t) {
            String w = raw.replaceAll("[^\\p{L}.]", "");
            if (w.equals("hundred") || w.equals("sau") || w.equals("सौ")) {
                current = (current == 0 ? 1 : current) * 100;
                found = true;
            } else if (w.equals("thousand") || w.equals("hazar") || w.equals("hazaar") || w.equals("हजार")) {
                total += (current == 0 ? 1 : current) * 1000;
                current = 0;
                found = true;
            } else if (w.equals("lakh") || w.equals("lac") || w.equals("लाख")) {
                total += (current == 0 ? 1 : current) * 100000;
                current = 0;
                found = true;
            } else {
                Double v = NUM.get(w);
                if (v != null) {
                    current += v;
                    found = true;
                }
            }
        }
        return found ? total + current : 0;
    }

    private static String extractPerson(String s) {
        String[] markers = {" को ", " से ", " ko ", " se "};
        for (String marker : markers) {
            int idx = s.indexOf(marker);
            if (idx > 0) {
                String left = s.substring(0, idx).trim();
                left = left.replaceAll("^(आज|कल|aaj|kal|मैंने|maine|हमने|hamne)\\s+", "");
                String[] words = left.split(" ");
                int start = Math.max(0, words.length - 3);
                StringBuilder out = new StringBuilder();
                for (int i = start; i < words.length; i++) {
                    if (words[i].matches(".*\\d.*")) continue;
                    if (out.length() > 0) out.append(' ');
                    out.append(words[i]);
                }
                return out.toString().trim();
            }
        }
        return "";
    }

    private static LocalDateTime parseDateTime(String s) {
        LocalDate date = LocalDate.now();
        if (containsAny(s, "परसों", "parso")) date = date.plusDays(2);
        else if (containsAny(s, "कल", "kal", "tomorrow")) date = date.plusDays(1);
        else {
            Map<String, DayOfWeek> days = new HashMap<>();
            days.put("सोमवार", DayOfWeek.MONDAY); days.put("monday", DayOfWeek.MONDAY); days.put("somvar", DayOfWeek.MONDAY);
            days.put("मंगलवार", DayOfWeek.TUESDAY); days.put("tuesday", DayOfWeek.TUESDAY); days.put("mangalvar", DayOfWeek.TUESDAY);
            days.put("बुधवार", DayOfWeek.WEDNESDAY); days.put("wednesday", DayOfWeek.WEDNESDAY); days.put("budhvar", DayOfWeek.WEDNESDAY);
            days.put("गुरुवार", DayOfWeek.THURSDAY); days.put("thursday", DayOfWeek.THURSDAY); days.put("guruvar", DayOfWeek.THURSDAY);
            days.put("शुक्रवार", DayOfWeek.FRIDAY); days.put("friday", DayOfWeek.FRIDAY); days.put("shukravar", DayOfWeek.FRIDAY);
            days.put("शनिवार", DayOfWeek.SATURDAY); days.put("saturday", DayOfWeek.SATURDAY); days.put("shanivar", DayOfWeek.SATURDAY);
            days.put("रविवार", DayOfWeek.SUNDAY); days.put("sunday", DayOfWeek.SUNDAY); days.put("ravivar", DayOfWeek.SUNDAY);
            for (Map.Entry<String,DayOfWeek> e : days.entrySet()) {
                if (s.contains(e.getKey())) {
                    date = date.with(TemporalAdjusters.next(e.getValue()));
                    break;
                }
            }
        }

        int hour = 9, minute = 0;
        Matcher tm = Pattern.compile("(\\d{1,2})(?:[:.](\\d{2}))?\\s*(?:बजे|baje|am|pm)?").matcher(s);
        if (tm.find()) {
            try {
                int h = Integer.parseInt(tm.group(1));
                int min = tm.group(2) == null ? 0 : Integer.parseInt(tm.group(2));
                if (containsAny(s, "शाम", "shaam", "evening", "pm") && h < 12) h += 12;
                if (containsAny(s, "रात", "raat") && h < 12) h += 12;
                if (h <= 23 && min <= 59) { hour = h; minute = min; }
            } catch (Exception ignored) {}
        } else if (containsAny(s, "दोपहर", "afternoon")) hour = 14;
        else if (containsAny(s, "शाम", "shaam", "evening")) hour = 18;
        else if (containsAny(s, "रात", "raat", "night")) hour = 20;
        else if (containsAny(s, "सुबह", "subah", "morning")) hour = 9;

        return LocalDateTime.of(date, LocalTime.of(hour, minute));
    }

    private static String cleanTaskTitle(String raw) {
        return raw.replaceAll("(?i)याद दिलाना|याद दिला देना|remind me|remind|कल|परसों|tomorrow|आज", "")
                .replaceAll("\\s+", " ").trim();
    }

    private static boolean containsAny(String s, String... values) {
        for (String v : values) if (s.contains(v)) return true;
        return false;
    }
}
