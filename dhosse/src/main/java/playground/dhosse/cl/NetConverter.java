package playground.dhosse.cl;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkWriter;
import org.matsim.core.network.NodeImpl;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.gis.ShapeFileWriter;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.io.OsmNetworkReader;
import org.opengis.feature.simple.SimpleFeature;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;

public class NetConverter {
	
	public void createNetwork(String osmFile, String outputFile){
		
		Config config = ConfigUtils.createConfig();
		Scenario scenario = ScenarioUtils.createScenario(config);
		Network network = scenario.getNetwork();
		CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, "EPSG:32719");
		OsmNetworkReader onr = new OsmNetworkReader(network, ct);
		onr.parse(osmFile);
		new NetworkCleaner().run(network);
		
		new NetworkWriter(network).write(outputFile);
		
	}
	
	public void convertCoordinates(Network net, String outputFile){
		
		CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation("EPSG:3857", "EPSG:32719");
		
		for(Node node : net.getNodes().values()){
			Coord newCoord = ct.transform(node.getCoord());
			((NodeImpl)node).setCoord(newCoord);
		}
		
		new NetworkWriter(net).write(outputFile);
		
	}
	
	public void convertNet2Shape(Network net, String outputFile){
		
		SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
		typeBuilder.setName("shape");
		typeBuilder.add("type", LineString.class);
		typeBuilder.add("id", String.class);
		SimpleFeatureBuilder builder = new SimpleFeatureBuilder(typeBuilder.buildFeatureType());
		
		List<SimpleFeature> features = new ArrayList<SimpleFeature>();

		for(Link link : net.getLinks().values()){
		
			Coord from = link.getFromNode().getCoord();
			Coord to = link.getToNode().getCoord();
			
			double fromX = from.getX();
			double fromY = from.getY();
			double toX = to.getX();
			double toY = to.getY();
			
			SimpleFeature feature = builder.buildFeature(null, new Object[]{
				new GeometryFactory().createLineString(new Coordinate[]{
						new Coordinate(fromX, fromY), new Coordinate(toX, toY)
				}),
				link.getId().toString()
			});
			features.add(feature);
			
		}
		
		ShapeFileWriter.writeGeometries(features, outputFile);
		
	}
	
	public void convertCounts2Shape(String inputFile, String outputFile){
		
		BufferedReader reader = IOUtils.getBufferedReader(inputFile);

		SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
		typeBuilder.setName("shape");
		typeBuilder.add("type", Point.class);
		typeBuilder.add("id", String.class);
		typeBuilder.add("orientation", String.class);
		SimpleFeatureBuilder builder = new SimpleFeatureBuilder(typeBuilder.buildFeatureType());
		
		List<SimpleFeature> features = new ArrayList<SimpleFeature>();
		
		try{
			
			String line = reader.readLine();
			
			while((line = reader.readLine()) != null){
				
				String[] splittedLine = line.split(";");
				
				String pc = splittedLine[0];
				String sentido = splittedLine[6];
				String x = splittedLine[7];
				x = x.replace(',', '.');
				String y = splittedLine[8];
				y = y.replace(',', '.');
				
				SimpleFeature feature = builder.buildFeature(null, new Object[]{
						new GeometryFactory().createPoint(new Coordinate(Double.parseDouble(x), Double.parseDouble(y))),
						pc,
						sentido
					});
					features.add(feature);
				
			}
			
			reader.close();
			
		} catch(IOException e){
			
		}
		
		ShapeFileWriter.writeGeometries(features, outputFile);
		
	}
	
	public void plans2Shape(Population population, String outputFile){
	
		SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
		typeBuilder.setName("shape");
		typeBuilder.add("geometry", Point.class);
		typeBuilder.add("id", String.class);
		typeBuilder.add("actType", String.class);
		SimpleFeatureBuilder builder = new SimpleFeatureBuilder(typeBuilder.buildFeatureType());
		
		List<SimpleFeature> features = new ArrayList<SimpleFeature>();
		
		for(Person person : population.getPersons().values()){
			
			for(PlanElement pe : person.getSelectedPlan().getPlanElements()){
				
				if(pe instanceof Activity){
					
					Activity act = (Activity)pe;
					Coord coord = act.getCoord();
					
					SimpleFeature feature = builder.buildFeature(null, new Object[]{
							new GeometryFactory().createPoint(new Coordinate(coord.getX(), coord.getY())),
							person.getId().toString(),
							act.getType()
						});
						features.add(feature);
					
				}
				
			}
			
		}
		
		ShapeFileWriter.writeGeometries(features, outputFile);
		
	}

}
