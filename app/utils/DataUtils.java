package utils;

import java.io.StringWriter;
import java.io.IOException;
import java.util.List;
import au.com.bytecode.opencsv.CSVWriter;
import proxies.Proxy;

public class DataUtils {
    public static String encodeCsv(List<Proxy> objects) {
        StringWriter out = new StringWriter(100000);
        CSVWriter writer = new CSVWriter(out);

        // write header
        writer.writeNext(objects.get(0).toHeader());
        
        // loop and write all lines
        for (Proxy object : objects) {
            writer.writeNext(object.toRow());
        }

        // since there are no files involved we don't need to worry about
        // IOExceptions.
        try {
            writer.close();
        } catch (IOException e) {}

        try {
            out.close();
        } catch (IOException e) {}

        return out.toString();
    }
}