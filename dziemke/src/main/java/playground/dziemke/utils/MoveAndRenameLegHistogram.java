package playground.dziemke.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.log4j.Logger;

public class MoveAndRenameLegHistogram {
	private final static Logger log = Logger.getLogger(MoveAndRenameLegHistogram.class);
	
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		// Parameters
		String runId = "run_132c";
		int iterationNumber = 150;
		
		// Input file and output directory
		String inputFile = "D:/Workspace/container/demand/output/" + runId + "/ITERS/it." + iterationNumber
				+ "/" + runId + "." + iterationNumber + ".legHistogram_all.png";
		String outputDirectory = "D:/VSP/Masterarbeit/Images/" + runId + "/";
		String outputFileName = "legHistogram.png";
		
		// Copy the file
		Path inputPath = Paths.get(inputFile);
		Path outputPath = Paths.get(outputDirectory + outputFileName);
				
		new File(outputDirectory).mkdir();

		Files.copy(inputPath, outputPath);
		
		log.info("Done creating the copied file " + outputDirectory + outputFileName + ".");
	}
}
