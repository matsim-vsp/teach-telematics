/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2014 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.michalm.supply.poznan;

import java.io.*;
import java.util.*;

import org.matsim.api.core.v01.*;
import org.matsim.contrib.dvrp.run.VrpConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.*;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import playground.michalm.zone.*;

import com.vividsolutions.jts.geom.*;


public class PoznanTaxiZoneReader
{
    private BufferedReader reader;

    private final Scenario scenario;
    private final Map<Id, Zone> zones = new LinkedHashMap<Id, Zone>();


    public PoznanTaxiZoneReader(Scenario scenario)
    {
        this.scenario = scenario;
    }


    public Map<Id, Zone> getZones()
    {
        return zones;
    }


    private void read(String txtFile)
        throws IOException
    {
        reader = new BufferedReader(new FileReader(txtFile));
        Map<String, Coord> coords = readCoords();
        readZones(coords);
        reader.close();
    }


    private Map<String, Coord> readCoords()
        throws IOException
    {
        CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation(
                TransformationFactory.WGS84, TransformationFactory.WGS84_UTM33N);
        Map<String, Coord> coords = new HashMap<String, Coord>();

        String firstLine = reader.readLine();
        if (!firstLine.equals("PUNKTY")) {
            throw new RuntimeException();
        }

        while (true) {
            String line = reader.readLine();
            if (line.equals("POSTOJE;CCW")) {
                return coords;
            }

            StringTokenizer st = new StringTokenizer(line, ",xy=");
            String id = st.nextToken();
            String x = st.nextToken();
            String y = st.nextToken();
            coords.put(id, ct.transform(new CoordImpl(x, y)));
        }
    }


    private void readZones(Map<String, Coord> coords)
        throws IOException
    {
        GeometryFactory geometryFactory = new GeometryFactory();

        while (true) {
            StringTokenizer st = new StringTokenizer(reader.readLine(), ",");
            String token = st.nextToken();

            if (token.equals("KONIEC")) {
                return;
            }
            else if (token.equals("TARYFA")) {
                continue;// skip this zone
            }

            Id zoneId = scenario.createId(token);
            int count = st.countTokens();
            Coordinate[] ringCoords = new Coordinate[count + 1];

            for (int i = 0; i < count; i++) {
                String coordId = st.nextToken();
                ringCoords[i] = MGC.coord2Coordinate(coords.get(coordId));
            }
            ringCoords[count] = ringCoords[0];

            LinearRing shell = geometryFactory.createLinearRing(ringCoords);
            Polygon polygon = geometryFactory.createPolygon(shell, null);
            zones.put(zoneId, new Zone(zoneId, "taxi_zone", polygon));
        }
    }


    public static void main(String[] args)
        throws IOException
    {
        String input = "d:/PP-rad/taxi/poznan-supply/dane/rejony/gps.txt";
        String zonesXmlFile = "d:/PP-rad/taxi/poznan-supply/dane/rejony/taxi_zones.xml";
        String zonesShpFile = "d:/PP-rad/taxi/poznan-supply/dane/rejony/taxi_zones.shp";

        Scenario scenario = ScenarioUtils.createScenario(VrpConfigUtils.createConfig());
        PoznanTaxiZoneReader zoneReader = new PoznanTaxiZoneReader(scenario);
        zoneReader.read(input);
        Map<Id, Zone> zones = zoneReader.getZones();
        Zones.writeZones(zones, TransformationFactory.WGS84_UTM33N, zonesXmlFile, zonesShpFile);
    }
}
