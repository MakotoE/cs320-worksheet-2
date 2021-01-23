import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Worksheet2 {
    public static void main(String[] args) throws IOException, XMLStreamException {
        String htmlString = readHTTP(new URL("https://www.bellevuecollege.edu/courses/exams/"));
//        String htmlString = Files.readString(Path.of("Final Exam Schedule Classes.html"));

        var matchInvalidXML = Pattern.compile(
                "(&[^;]*;)|(<meta[^>]*>)|(<script.*?</script>)",
                Pattern.DOTALL
        );
        htmlString = matchInvalidXML
                .matcher(htmlString)
                .replaceAll("");

        XMLStreamReader xml = XMLInputFactory
                .newInstance()
                .createXMLStreamReader(new StringReader(htmlString));

        System.out.println(YearlySchedule.readXML(xml));
    }

    @SuppressWarnings("unused")
    static String readHTTP(URL url) throws IOException {
        var connection = url.openConnection();
        connection.setRequestProperty("user-Agent", "Mozilla/5.0");
        var streamReader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);
        try (var reader = new BufferedReader(streamReader)) {
            return reader
                    .lines()
                    .collect(Collectors.joining("\n"));
        }
    }
}

class Entry {
    String classTime = "";
    String examDay = "";
    String examTime = "";

    // xml must begin with <tr> tag.
    static Entry readXML(XMLStreamReader xml) {
        Entry entry = new Entry();
        try {
            while (!Util.isAtStartTag(xml, "td")) {
                xml.next();
                if (Util.isAtEndTag(xml, "tr")) {
                    return entry;
                }
            }

            xml.next();
            if (xml.isCharacters()) {
                entry.classTime = xml.getText();

                if (entry.classTime.equals("6:30 a.m.")) {
                    entry.examDay = "Regular class hours";
                }
            }

            while (!Util.isAtStartTag(xml, "td")) {
                xml.next();
                if (Util.isAtEndTag(xml, "tr")) {
                    return entry;
                }
            }

            xml.next();
            if (xml.isCharacters()) {
                entry.examDay = xml.getText();
            }

            while (!Util.isAtStartTag(xml, "td")) {
                xml.next();
                if (Util.isAtEndTag(xml, "tr")) {
                    return entry;
                }
            }

            xml.next();
            if (xml.isCharacters()) {
                entry.examTime = xml.getText();
            }

            return entry;
        } catch (XMLStreamException ignored) {
        }

        return entry;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Entry entry = (Entry) o;
        return classTime.equals(entry.classTime) && examDay.equals(entry.examDay) && examTime.equals(
                entry.examTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classTime, examDay, examTime);
    }

    @Override
    public String toString() {
        return String.format(
                """
                        +++++++++++++++++++++++++++++++++++
                        Class Time: %s
                        Exam Day: %s
                        Exam Time: %s
                        """,
                classTime,
                examDay,
                examTime
        );
    }
}

class QuarterSchedule {
    ArrayList<Map.Entry<String, ArrayList<Entry>>> weekdaysStringToEntries = new ArrayList<>();

    void addEntries(String weekdaysString, ArrayList<Entry> entries) {
        weekdaysStringToEntries.add(Map.entry(weekdaysString, entries));
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (var weekdaySchedules : weekdaysStringToEntries) {
            builder.append(weekdaySchedules.getKey());
            builder.append("\n\n");
            for (var entry : weekdaySchedules.getValue()) {
                builder.append(entry);
            }
            builder.append("+++++++++++++++++++++++++++++++++++\n\n");
        }
        return builder.toString();
    }
}

class YearlySchedule {
    static YearlySchedule readXML(XMLStreamReader xml) {
        // <h2>Final exam days for Spring Quarter 2020</h2>
        // ...
        // <span>If your class meets...</span>
        // ...
        // <div class="table-responsive final-exam-table">
        //   ...
        //   <table class="table table-striped ">
        //     <thead>
        //     ...
        //     </thead>
        //     <tbody>
        //       <tr>
        //         <td>
        //           <!-- Class Time -->
        //         </td>
        //         <td>
        //           <!-- Exam Day -->
        //         </td>
        //         <td>
        //           <!-- Exam Time -->
        //         </td>
        //       </tr>
        //     </tbody>
        //   </table>
        //   ...
        // </div>
        // ...
        // <span>If your class meets...</span>
        // ...
        // <div class="table-responsive final-exam-table">
        //   ...
        //   <table>
        //     <!-- More schedules -->
        //   </table>

        YearlySchedule yearlySchedule = new YearlySchedule();

        try {
            String currQuarter = "";
            StringBuilder currWeekdayKeyword = new StringBuilder();

            while (xml.hasNext()) {
                xml.next();
                if (xml.isStartElement()) {
                    int attributeCount = xml.getAttributeCount();
                    for (int i = 0; i < attributeCount; ++i) {
                        if (xml.getAttributeValue(i).contains("final-exam-table")) {
                            while (!Util.isAtStartTag(xml, "tbody")) {
                                xml.next();
                            }

                            yearlySchedule.addSchedule(
                                    currQuarter,
                                    currWeekdayKeyword.toString(),
                                    readEntries(xml)
                            );
                            break;
                        }
                    }
                } else if (xml.isCharacters()) {
                    String text = xml.getText();
                    if (text.contains("Final exam days")) {
                        currQuarter = text.replaceAll("Final exam days for ", "");
                    } else if (text.contains("If your class meets")) {
                        currWeekdayKeyword = new StringBuilder();

                        while (!Util.isAtEndTag(xml, "span")) {
                            if (xml.isCharacters()) {
                                currWeekdayKeyword.append(xml.getText());
                            }
                            xml.next();
                        }
                    }
                }
            }
        } catch (XMLStreamException ignored) {
        }

        return yearlySchedule;
    }

    // xml must begin at the tbody tag.
    static ArrayList<Entry> readEntries(XMLStreamReader xml) {
        ArrayList<Entry> entries = new ArrayList<>();

        try {
            while (!Util.isAtStartTag(xml, "tr")) {
                xml.next();
                if (Util.isAtEndTag(xml, "tbody")) {
                    return entries;
                }
            }

            while (true) {
                while (!Util.isAtStartTag(xml, "tr")) {
                    xml.next();
                    if (Util.isAtEndTag(xml, "tbody")) {
                        return entries;
                    }
                }

                xml.next();
                entries.add(Entry.readXML(xml));

                while (!Util.isAtEndTag(xml, "tr")) {
                    xml.next();
                    if (Util.isAtEndTag(xml, "tbody")) {
                        return entries;
                    }
                }
            }
        } catch (XMLStreamException ignored) {
        }

        return entries;
    }

    ArrayList<Map.Entry<String, QuarterSchedule>> schedules = new ArrayList<>();

    void addSchedule(
            String quarterKeyword,
            String weekdayKeyword,
            ArrayList<Entry> entries
    ) {
        for (var schedule : schedules) {
            if (schedule.getKey().equals(quarterKeyword)) {
                schedule.getValue().addEntries(weekdayKeyword, entries);
                return;
            }
        }

        var quarter = new QuarterSchedule();
        quarter.addEntries(weekdayKeyword, entries);
        schedules.add(Map.entry(quarterKeyword, quarter));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        YearlySchedule that = (YearlySchedule) o;
        return schedules.equals(that.schedules);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schedules);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (var quarter : schedules) {
            builder.append(quarter.getKey());
            builder.append("\n\n");
            builder.append(quarter.getValue().toString());
        }
        return builder.toString();
    }
}

class Util {
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    static boolean isAtStartTag(XMLStreamReader xml, String tagName) {
        return xml.isStartElement() && xml.getName().toString().equals(tagName);
    }

    static boolean isAtEndTag(XMLStreamReader xml, String tagName) {
        return xml.isEndElement() && xml.getName().toString().equals(tagName);
    }
}