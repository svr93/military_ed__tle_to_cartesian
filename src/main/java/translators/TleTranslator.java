package translators;

import constants.PlanetConstants;

import agi.foundation.Constants;
import agi.foundation.coordinates.KeplerianElements;
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
import org.json.JSONArray;
import org.json.JSONObject;

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
    private String rfName = null;

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
            this.rfName = "FIXED";
        } else if (targetCS.equals("ECI")) {

            this.rf = earth.getInertialFrame();
            this.rfName = "INERTIAL";
        } else {

            throw new Error("Coordinate system is not supported");
        }
    }

    /**
     * Gets semi-major axis from mean motion
     * (source: http://satelliteorbitdetermination.com/orbit_elements_wiki.htm).
     * @return Semi-major axis in meters.
     */
    private double getSemiMajorAxis() {

        double meanMotion = this.tle.getMeanMotion();
        return (6.6228 / Math.pow(meanMotion, 2 / 3)) * PlanetConstants.EarthRadiusM;
    }

    /**
     * Gets true anomaly from mean anomaly.
     * (source: http://www.stargazing.net/kepler/kepler.html#twig02b).
     * Analog: `KeplerianElements instance`.meanAnomalyToTrueAnomaly().
     * @param meanAnomalyR Mean anomaly in radians.
     * @param ecc The eccentricity of the orbit.
     * @return True anomaly in radians.
     */
    private double getTrueAnomaly(double meanAnomalyR, double ecc) {

        final double eps14 = Constants.Epsilon14;

        double e = meanAnomalyR;
        double delta = 0.1;
        double twoPi = Constants.TwoPi;

        while (Math.abs(delta) >= eps14) {

            delta = e - ecc * Math.sin(e) - meanAnomalyR;
            e = e - delta / (1 - ecc * Math.cos(e));
        }
        double v = 2 * Math.atan(Math.sqrt((1 + ecc) / (1 - ecc)) * Math.tan(0.5 * e));
        if (v < 0) { v = v + twoPi; }
        return v;
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

    protected DateMotionCollection1<Cartesian> propagateInitialData(
        JulianDate startDate, JulianDate stopDate, double interval) {

        Duration timeStep = new Duration(0, interval);
        return this.sgp4Propagator.propagate(startDate, stopDate, timeStep, 0, this.rf);
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

        DateMotionCollection1<Cartesian> collection = this.propagateInitialData(
            startDate, stopDate, interval);

        List<JulianDate> dates = collection.getDates();
        List<Cartesian> coordinates = collection.getValues();

        int size = collection.getCount();
        Map<DateTime, Cartesian> resMap = new LinkedHashMap<>();

        for (int i = 0; i < size; ++i) {

            resMap.put(dates.get(i).toDateTime(), coordinates.get(i));
        }
        return resMap;
    }

    public JSONObject propagateJSON(JulianDate startDate, JulianDate stopDate, double interval) {

        DateMotionCollection1<Cartesian> collection = this.propagateInitialData(
        startDate, stopDate, interval);

        List<JulianDate> dates = collection.getDates();
        List<Cartesian> coordinates = collection.getValues();

        int size = collection.getCount();
        JSONObject resObj = new JSONObject();

        JSONObject position = new JSONObject();
        position.put("referenceFrame", this.rfName);

        JSONArray cartesian = new JSONArray();
        for (int i = 0; i < size; ++i) {

            cartesian.put(dates.get(i).toDateTime());

            Cartesian coordinate = coordinates.get(i);
            cartesian.put(coordinate.getX());
            cartesian.put(coordinate.getY());
            cartesian.put(coordinate.getZ());
        }
        position.put("cartesian", cartesian);
        resObj.put("position", position);

        return resObj;
    }

    public KeplerianElements convertToKeplerianElements() {

        final double radiansPerDegree = Constants.RadiansPerDegree;

        final double semiMajorAxisM = this.getSemiMajorAxis();
        final double eccentricity = this.tle.getEccentricity();

        final double inclinationR = this.tle.getInclination() * radiansPerDegree;
        final double argumentOfPerigeeR = this.tle.getArgumentOfPerigee() * radiansPerDegree;
        final double rightAscensionOfAscendingNodeR = this.tle.getRightAscensionOfAscendingNode() *
            radiansPerDegree;

        final double meanAnomalyR = this.tle.getMeanAnomaly() * radiansPerDegree;
        final double trueAnomalyR = this.getTrueAnomaly(meanAnomalyR, eccentricity);
        final double gravitationalParameterSI = PlanetConstants.gravitationalParameterSI;

        return new KeplerianElements(
            semiMajorAxisM,
            eccentricity,
            inclinationR,
            argumentOfPerigeeR,
            rightAscensionOfAscendingNodeR,
            trueAnomalyR,
            gravitationalParameterSI
        );
    }
}
