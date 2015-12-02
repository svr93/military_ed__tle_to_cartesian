package translators;

import agi.foundation.DateMotionCollection1;
import agi.foundation.celestial.CentralBodiesFacet;
import agi.foundation.celestial.EarthCentralBody;
import agi.foundation.coordinates.Cartesian;
import agi.foundation.geometry.ReferenceFrame;
import agi.foundation.propagators.Sgp4Propagator;
import agi.foundation.propagators.TwoLineElementSet;
import agi.foundation.time.Duration;
import agi.foundation.time.JulianDate;

import org.joda.time.DateTime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by shamsheev on 26.11.15.
 */
public class TleTranslator {

    private final TwoLineElementSet tle;

    private Sgp4Propagator sgp4Propagator = null;
    private ReferenceFrame rf = null;

    private void initPropagatorSettings(String targetCS) {

        this.setSgp4Propagator();
        this.setReferenceFrame(targetCS);
    }

    private void setSgp4Propagator() {

        this.sgp4Propagator = new Sgp4Propagator(this.tle);
    }

    /**
     * Sets reference frame (agi - ReferenceFramesAndTransformations.html).
     * @param targetCS Coordinate system.
     * ECEF ("Earth-Centered, Earth-Fixed", "ГПСК") [rotates with the Earth];
     * ECI ("Earth-centered inertial") coordinate systems.
     */
    private void setReferenceFrame(String targetCS) {

        EarthCentralBody earth = CentralBodiesFacet.getFromContext().getEarth();

        if (targetCS.equals("ECEF")) {

            this.rf = earth.getFixedFrame();
        } else if (targetCS.equals("ECI")) {

            this.rf = earth.getInertialFrame();
        } else {

            throw new Error("Coordinate system is not supported");
        }
    }

    /**
     * Gets semi-major axis from mean motion
     * (source: http://satelliteorbitdetermination.com/orbit_elements_wiki.htm).
     * @return Semi-major axis in Earth radii.
     */
    private double getSemiMajorAxis() {

        double meanMotion = this.tle.getMeanMotion();
        return 6.6228 / Math.pow(meanMotion, 2 / 3);
    }

    /**
     * Creates Julian date from current date data.
     * @param year The year.
     * @param monthOfYear The month of the year, from 1 to 12.
     * @param dayOfMonth The day of the month, from 1 to 31.
     * @param hourOfDay The hour of the day, from 0 to 23.
     * @param minuteOfHour The minute of the hour, from 0 to 59.
     * @return Converted date.
     */
    public static JulianDate createJulianDate(
        int year, int monthOfYear, int dayOfMonth, int hourOfDay, int minuteOfHour
    ) {

        DateTime dt = new DateTime(year, monthOfYear, dayOfMonth, hourOfDay, minuteOfHour);
        return new JulianDate(dt);
    }

    public TleTranslator(String firstLine, String secondLine, String targetCS) {

        this.tle = new TwoLineElementSet(firstLine + "\n" + secondLine + "\n");
        this.initPropagatorSettings(targetCS);
    }

    /**
     * Calculates points.
     * @param startDate Start date for computation.
     * @param stopDate Finish date for computation.
     * @param interval Time step in seconds.
     * @return Computation result.
     */
    public Map<DateTime, Cartesian> propagate(
        JulianDate startDate, JulianDate stopDate, double interval
    ) {

        Duration timeStep = new Duration(0, interval);
        DateMotionCollection1<Cartesian> collection = this.sgp4Propagator.propagate(
            startDate, stopDate, timeStep, 0, this.rf
        );

        List<JulianDate> dates = collection.getDates();
        List<Cartesian> coordinates = collection.getValues();

        int size = collection.getCount();
        Map<DateTime, Cartesian> resMap = new LinkedHashMap<>();

        for (int i = 0; i < size; ++i) {

            resMap.put(dates.get(i).toDateTime(), coordinates.get(i));
        }
        return resMap;
    }
}
