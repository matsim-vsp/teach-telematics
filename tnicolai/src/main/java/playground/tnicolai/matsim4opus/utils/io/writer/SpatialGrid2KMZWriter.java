package playground.tnicolai.matsim4opus.utils.io.writer;

import gnu.trove.TObjectDoubleHashMap;

import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.contrib.matsim4opus.gis.SpatialGrid;

import playground.tnicolai.matsim4opus.constants.Constants;
import playground.tnicolai.matsim4opus.gis.MyColorizer;
import playground.tnicolai.matsim4opus.gis.io.FeatureKMLWriter;
import playground.tnicolai.matsim4opus.utils.helperObjects.SquareLayer;

import com.vividsolutions.jts.geom.Geometry;

public class SpatialGrid2KMZWriter {
	
	private static final Logger log = Logger.getLogger(SpatialGrid2KMZWriter.class);
	
	public static void writeKMZFiles(final SpatialGrid<SquareLayer> travelTimeAccessibilityGrid,
									 final SpatialGrid<SquareLayer> travelCostAccessibilityGrid,
									 final SpatialGrid<SquareLayer> travelDistanceAccessibilityGrid) {
		
		log.info("Writing Google Erath files ...");
		
		assert (travelTimeAccessibilityGrid != null);
		assert (travelDistanceAccessibilityGrid != null);
		assert (travelCostAccessibilityGrid != null);
		
		log.info("Writing " + Constants.ERSA_TRAVEL_TIME_ACCESSIBILITY + " ...");
		write(travelTimeAccessibilityGrid, Constants.ERSA_TRAVEL_TIME_ACCESSIBILITY);
		log.info("Writing " + Constants.ERSA_TRAVEL_DISTANCE_ACCESSIBILITY + " ...");
		write(travelDistanceAccessibilityGrid, Constants.ERSA_TRAVEL_DISTANCE_ACCESSIBILITY);
		log.info("Writing " + Constants.ERSA_TRAVEL_COST_ACCESSIBILITY + " ...");
		write(travelCostAccessibilityGrid, Constants.ERSA_TRAVEL_COST_ACCESSIBILITY);
		
		log.info("Done with writing Google Erath files ...");
	}
	
	private static void write(SpatialGrid<SquareLayer> grid, String type){
		
		FeatureKMLWriter writerCentroid = new FeatureKMLWriter();
		FeatureKMLWriter writerMean = new FeatureKMLWriter();
		FeatureKMLWriter writerDerivation = new FeatureKMLWriter();
		Set<Geometry> geometries = new HashSet<Geometry>();
		
		TObjectDoubleHashMap<Geometry> centroidValues = new TObjectDoubleHashMap<Geometry>();
		TObjectDoubleHashMap<Geometry> meanValues = new TObjectDoubleHashMap<Geometry>();
		TObjectDoubleHashMap<Geometry> derivationValues = new TObjectDoubleHashMap<Geometry>();
		
		int rows = grid.getNumRows();
		int cols = grid.getNumCols(0);
		log.info("Grid Rows: " + rows + " Grid Columns: " + cols);
		
		int total = 0;
		int skipped = 0;
		
		for (int r = 0; r < rows; r++) {
			for (int c = 0; c < cols; c++) {
				total++;
				SquareLayer layer = grid.getValue(r, c);
				
				if(layer != null && layer.getPolygon() != null){
					
					geometries.add( layer.getPolygon() );
					centroidValues.put(layer.getPolygon(), layer.getCentroidAccessibility());
					meanValues.put(layer.getPolygon(), layer.getMeanAccessibility());
					derivationValues.put(layer.getPolygon(), layer.getAccessibilityDerivation());
				}
				else
					skipped++;
			}
		}
		log.info("Processed " + total + " squares " + skipped + " of them where skipped!");
		
		// writing centroid values
		log.info("Writing centroid values ...");
		writerCentroid.setColorizable(new MyColorizer( centroidValues ));
		writerCentroid.write(geometries, Constants.MATSIM_4_OPUS_TEMP + Constants.ERSA_CENTROID + type + Constants.FILE_TYPE_CSV);
		// writing mean values
		log.info("writing mean values ...");
		writerMean.setColorizable(new MyColorizer( meanValues ));
		writerMean.write(geometries, Constants.MATSIM_4_OPUS_TEMP + Constants.ERSA_MEAN + type + Constants.FILE_TYPE_CSV);
		// writing derivation values
		log.info("writing derivation values ...");
		writerDerivation.setColorizable(new MyColorizer( derivationValues ));
		writerDerivation.write(geometries, Constants.MATSIM_4_OPUS_TEMP + Constants.ERSA_DERIVATION + type + Constants.FILE_TYPE_CSV);	
		
		log.info("... done!");
	}

}
