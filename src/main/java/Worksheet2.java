import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Worksheet2 {
    public static void main(String[] args) throws IOException, XMLStreamException {
//        String htmlString = readHTTP(new URL("https://www.bellevuecollege.edu/courses/exams/"));
        String htmlString = Files.readString(Path.of("Final Exam Schedule Classes.html"));

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

        var tables = readTables(xml);
        System.out.println(tables);
    }

    @SuppressWarnings("unused")
    static String readHTTP(URL url) throws IOException {
        var connection = url.openConnection();
        connection.setRequestProperty("user-Agent", "Mozilla/5.0");
        try (var reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            return reader
                    .lines()
                    .collect(Collectors.joining("\n"));
        }
    }

    static YearlySchedule readTables(XMLStreamReader xml) throws XMLStreamException {
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
        String currQuarter = "";
        StringBuilder currWeekdayKeyword = new StringBuilder();

        while (xml.hasNext()) {
            xml.next();
            if (xml.isStartElement()) {
                int attributeCount = xml.getAttributeCount();
                for (int i = 0; i < attributeCount; ++i) {
                    if (xml.getAttributeValue(i).contains("final-exam-table")) {
                        while (!isAtStartTag(xml, "tbody")) {
                            xml.next();
                        }

                        yearlySchedule.putSchedule(
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

                    while (!isAtEndTag(xml, "span")) {
                        if (xml.isCharacters()) {
                            currWeekdayKeyword.append(xml.getText());
                        }
                        xml.next();
                    }
                }
            }
        }

        return yearlySchedule;
    }

    // xml must begin at the tbody tag.
    static ArrayList<Entry> readEntries(XMLStreamReader xml) throws XMLStreamException {
        ArrayList<Entry> entries = new ArrayList<>();
        while (!isAtStartTag(xml, "tr")) {
            xml.next();
            if (isAtEndTag(xml, "tbody")) {
                return entries;
            }
        }

        while (true) {
            while (!isAtStartTag(xml, "tr")) {
                xml.next();
                if (isAtEndTag(xml, "tbody")) {
                    return entries;
                }
            }

            xml.next();
            entries.add(readEntry(xml));

            while (!isAtEndTag(xml, "tr")) {
                xml.next();
                if (isAtEndTag(xml, "tbody")) {
                    return entries;
                }
            }
        }
    }

    // xml must begin with <tr> tag.
    static Entry readEntry(XMLStreamReader xml) throws XMLStreamException {
        Entry entry = new Entry();

        while (!isAtStartTag(xml, "td")) {
            xml.next();
            if (isAtEndTag(xml, "tr")) {
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

        while (!isAtStartTag(xml, "td")) {
            xml.next();
            if (isAtEndTag(xml, "tr")) {
                return entry;
            }
        }

        xml.next();
        if (xml.isCharacters()) {
            entry.examDay = xml.getText();
        }

        while (!isAtStartTag(xml, "td")) {
            xml.next();
            if (isAtEndTag(xml, "tr")) {
                return entry;
            }
        }

        xml.next();
        if (xml.isCharacters()) {
            entry.examTime = xml.getText();
        }

        return entry;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    static boolean isAtStartTag(XMLStreamReader xml, String tagName) {
        return xml.isStartElement() && xml.getName().toString().equals(tagName);
    }

    static boolean isAtEndTag(XMLStreamReader xml, String tagName) {
        return xml.isEndElement() && xml.getName().toString().equals(tagName);
    }
}

class Entry {
    String classTime = "";
    String examDay = "";
    String examTime = "";

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

    public void addEntries(String weekdaysString, ArrayList<Entry> entries) {
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
    ArrayList<Map.Entry<String, QuarterSchedule>> schedules = new ArrayList<>();

    public void putSchedule(
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