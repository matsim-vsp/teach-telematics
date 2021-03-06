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
package playground.ikaddoura.analysis.airPollution;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.analysis.spatial.Grid;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.analysis.EmissionGridAnalyzer;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

/**
 * @author amit, ihab
 */

public class BerlinSpatialPlots {
	private static final Logger log = Logger.getLogger(BerlinSpatialPlots.class);

    private final double countScaleFactor;
    private final double gridSize;
    private final double smoothingRadius;

	private static final double xMin = 4565039. - 125.;
	private static final double xMax = 4632739. + 125.; 
	private static final double yMin = 5801108. - 125.;
    private static final double yMax = 5845708. + 125.;

    private BerlinSpatialPlots(final double gridSize, final double smoothingRadius, final double countScaleFactor) {
        this.gridSize = gridSize;
        this.smoothingRadius = smoothingRadius;
        this.countScaleFactor = countScaleFactor;
    }

	public static void main(String[] args) {
        
        final double gridSize = 250.;
        final double smoothingRadius = 500.;
        final double countScaleFactor = 10.;
        
//        final String runDir = "/Users/ihab/Documents/workspace/runs-svn/sav-pricing-setupA/output_bc-0/";
//    	final String runId = "bc-0";
    	
//    	final String runDir = "/Users/ihab/Documents/workspace/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.2-1pct/output-berlin-v5.2-1pct/";
//    	final String runId = "berlin-v5.2-1pct";
    	
//    	final String runDir = "/Users/ihab/Documents/workspace/runs-svn/sav-pricing-setupA/output_savA-0d/";
//    	final String runId = "savA-0d";
    	
//    	final String runDir = "/Users/ihab/Documents/workspace/runs-svn/sav-pricing-setupA/output_savA-2d/";
//    	final String runId = "savA-2d";
    	
    	final String runDir = "/Users/ihab/Documents/workspace/runs-svn/sav-pricing-setupA/output_savA-3d/";
    	final String runId = "savA-3d";

        BerlinSpatialPlots plots = new BerlinSpatialPlots(gridSize, smoothingRadius, countScaleFactor);
        
        final String configFile = runDir + runId + ".output_config.xml";
		final String events = runDir + runId + ".200.emission.events.offline_2019-02-07.xml.gz";
		final String outputFile = runDir + runId + ".200.NOx_2019-02-07.csv";
		
		plots.writeEmissionsToCSV(configFile , events, outputFile);
    }

    private void writeEmissionsToCSV(String configPath, String eventsPath, String outputPath) {

        Config config = ConfigUtils.loadConfig(configPath.toString());
		config.plans().setInputFile(null);
        Scenario scenario = ScenarioUtils.loadScenario(config);

        double binSize = 200000; // make the bin size bigger than the scenario has seconds
        Network network = scenario.getNetwork();

        EmissionGridAnalyzer analyzer = new EmissionGridAnalyzer.Builder()
                .withGridSize(gridSize)
                .withTimeBinSize(binSize)
                .withNetwork(network)
                .withBounds(createBoundingBox())
                .withSmoothingRadius(smoothingRadius)
                .withCountScaleFactor(countScaleFactor)
                .withGridType(EmissionGridAnalyzer.GridType.Square)
                .build();

        TimeBinMap<Grid<Map<Pollutant, Double>>> timeBins = analyzer.process(eventsPath.toString() );

        log.info("Writing to csv...");
        writePollutantGridToCSV(timeBins, Pollutant.NOx, outputPath );
    }

    private void writePollutantGridToCSV( TimeBinMap<Grid<Map<Pollutant, Double>>> bins, Pollutant pollutant, String outputPath ) {
            // this method originally used "String" instead of "Pollutant".  After the emissions material was changed (back) to enum types, I also changed
            // this method here (back) to enum.  I have, however, not tested it.  kai, feb'20
            // (yy I am also not sure why a "Berlin" method sits in a playground and not in the "Berlin" repo. kai, feb'20)

        try (CSVPrinter printer = new CSVPrinter(new FileWriter(outputPath.toString()), CSVFormat.TDF)) {
            printer.printRecord("timeBinStartTime", "centroidX", "centroidY", "weight");

            for (TimeBinMap.TimeBin<Grid<Map<Pollutant, Double>>> bin : bins.getTimeBins()) {
                final double timeBinStartTime = bin.getStartTime();
                for (Grid.Cell<Map<Pollutant, Double>> cell : bin.getValue().getCells()) {
                    double weight = cell.getValue().containsKey(pollutant) ? cell.getValue().get(pollutant) : 0;
                    printer.printRecord(timeBinStartTime, cell.getCoordinate().x, cell.getCoordinate().y, weight);
				}
			}
        } catch (IOException e) {
            e.printStackTrace();
		}
	}

    private Geometry createBoundingBox() {
        return new GeometryFactory().createPolygon(new Coordinate[]{
                new Coordinate(xMin, yMin), new Coordinate(xMax, yMin),
                new Coordinate(xMax, yMax), new Coordinate(xMin, yMax),
                new Coordinate(xMin, yMin)
        });
    }
}
