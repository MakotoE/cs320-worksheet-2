import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;

public class Worksheet2_test {
    @Test
    public void testEntryReadXML() throws XMLStreamException {
        {
            String s = "";
            var result = Entry.readXML(xmlFromString(s));
            Assertions.assertEquals(result, new Entry());
        }
        {
            String s = """
                    <tr>
                        <td>classTime</td>
                        <td>examDay</td>
                        <td>examTime</td>
                    </tr>
                    """;

            var result = Entry.readXML(xmlFromString(s));
            var expected = new Entry();
            expected.classTime = "classTime";
            expected.examDay = "examDay";
            expected.examTime = "examTime";
            Assertions.assertEquals(result, expected);
        }
    }

    @Test
    public void testYearlyScheduleReadXML() throws XMLStreamException {
        var result = YearlySchedule.readXML(xmlFromString(""));
        Assertions.assertEquals(result, new YearlySchedule());
    }

    static XMLStreamReader xmlFromString(String s) throws XMLStreamException {
        return XMLInputFactory
                .newInstance()
                .createXMLStreamReader(new StringReader(s));
    }
}
