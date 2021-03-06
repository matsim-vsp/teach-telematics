package playground.dziemke.utils;

import org.locationtech.jts.geom.Geometry;
import org.matsim.contrib.accessibility.GridUtils;

public class ShapeFileAnalyzer {
	static String shapeFileName = "../../data/cemdapMatsimCadyts/input/shapefiles/Berlin_DHDN_GK4.shp";

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		Geometry boundary = GridUtils.getBoundary(shapeFileName );
		
		// System.out.println("boundary.getEnvelope() = " + boundary.getEnvelope());
		
		System.out.println("The extent of the shapefile is "+ boundary.getEnvelopeInternal());

	}

}
