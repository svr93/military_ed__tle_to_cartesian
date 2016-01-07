import agi.foundation.time.JulianDate;
import agi.foundation.coordinates.Cartesian;

import org.joda.time.DateTime;

import java.util.Map;

import org.json.JSONObject;
import org.json.JSONArray;

import translators.TleTranslator;

/**
 * Created by shamsheev on 24.11.15.
 */
public class Main {

    public static void main(String[] args) {

        TleTranslator tleTranslator = new TleTranslator(
            "1 20724U 90068A   02173.73395695 -.00000086  00000-0  00000-0 0  1771",
            "2 20724  56.1487  21.0845 0183651 226.6216 131.8780  2.00562381 85500",
            "ECEF" // rotates with the Earth
        );
        JulianDate startDate = TleTranslator.createJulianDate(2015, 11, 26, 0, 0);
        JulianDate stopDate = TleTranslator.createJulianDate(2015, 11, 27, 0, 0);

        final double interval = 3600; // time step in seconds

        Map<DateTime, Cartesian> result = tleTranslator.propagate(startDate, stopDate, interval);
        JSONObject obj = new JSONObject();

        result.forEach((key, value) -> {

            JSONArray arr = new JSONArray();

            arr.put(value.getX());
            arr.put(value.getY());
            arr.put(value.getZ());

            obj.put(key.toString(), arr);
        });
        System.out.println(obj);
    }
}
