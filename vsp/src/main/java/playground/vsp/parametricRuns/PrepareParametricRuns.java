/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
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

package playground.vsp.parametricRuns;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Properties;
import com.google.common.base.Charsets;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

/**
 * A class to create a job script, write it on remote and then run the job based on the given parameters.
 *
 * (a) create a public-private key pair as (it does not work if password is set).
 *          'ssh-keygen -t rsa -b 4096 -f $HOME/.ssh/nameOfTheKey'
 * (b) copy public key ('nameOfTheKey.pub') to cluster as
 *          'ssh-copy-id -i nameOfTheKey.pub agarwal@cluster-i.math.tu-berlin.de'
 *
 * Created by amit on 04.10.17.
 */

public class PrepareParametricRuns {

    public static final String newLine = System.getProperty("line.separator");
    private final Session session;
    private final ChannelSftp sftp;
    private final String userName;
    private String jobParams = "";

    public PrepareParametricRuns(String pathToKnownHosts, String pathToPrivateKey, String userName) {
    this.userName = userName;
    	try {
            JSch jSch = new JSch();
            jSch.setKnownHosts(pathToKnownHosts); // location of the ssh fingerprint (unique host key)
            jSch.addIdentity(pathToPrivateKey); // this is the private key required.

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no"); // so that no question asked, and script run without any problem

            session = jSch.getSession(userName, "cluster-i.math.tu-berlin.de", 22);
            session.setConfig(config);

            session.connect();

            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect();
        } catch (JSchException e) {
            throw new RuntimeException("Aborting. Reason : " + e);
        }
        System.out.println("Session is connected. Please note that, a few default parameters (see "+JobScriptWriter.class.getSimpleName() + " for default params) are used in the job script."
                +"\nTo override these, pass the options to appendJobParameters() as follows: "
                +"\n \t \t parametricRuns.appendJobParameters(\"-M myself@math.tu-berlin.de\"); \t \t"
                +"\n \t \t parametricRuns.appendJobParameters(\"-N jobName\"); \t \t"
                +"\nHere is a list of parameters: http://www.math.tu-berlin.de/iuk/forschungsrechnerbereich/service/cluster_nutzung/parameter/en/");
    }

    public static void main(String[] args) {
        int runCounter= 100;

        String baseDir = "/net/ils4/agarwal/equilOpdyts/carPt/output/";
        String matsimDir = "r_d24e170ecef8172430381b23c72f39e3f9e79ea1_opdyts_22Oct";

        StringBuilder buffer = new StringBuilder();

        PrepareParametricRuns parametricRuns = new PrepareParametricRuns("~/.ssh/known_hosts","~/.ssh/id_rsa_tub_math","agarwal");

        String ascStyles [] = {"axial_fixedVariation","axial_randomVariation"};
        double [] stepSizes = {0.25, 0.5, 1.0};
        Integer [] convIterations = {300};
        double [] selfTuningWts = {1.0};
        Integer [] warmUpIts = {1, 5, 10};

        buffer.append("runNr\tascStyle\tstepSize\titerations2Convergence\tselfTunerWt\twarmUpIts"+newLine);

        for (String ascStyle : ascStyles ) {
            for(double stepSize :stepSizes){
                for (int conIts : convIterations) {
                    for (double selfTunWt : selfTuningWts) {
                        for (int warmUpIt : warmUpIts) {

                            String arg = ascStyle + " "+ stepSize + " " + conIts + " " + selfTunWt + " " + warmUpIt;
                            String jobName = "run"+String.valueOf(runCounter++);

                            String [] additionalLines = {
                                    "echo \"========================\"",
                                    "echo \" "+matsimDir+" \" ",
                                    "echo \"========================\"",
                                    newLine,

                                    "cd /net/ils4/agarwal/matsim/"+matsimDir+"/",
                                    newLine,

                                    "java -Djava.awt.headless=true -Xmx29G -cp agarwalamit-0.10.0-SNAPSHOT.jar " +
                                            "playground/agarwalamit/opdyts/equil/MatsimOpdytsEquilIntegration " +
                                            "/net/ils4/agarwal/equilOpdyts/carPt/inputs/ " +
                                            "/net/ils4/agarwal/equilOpdyts/carPt/output/"+jobName+"/ " +
                                            "/net/ils4/agarwal/equilOpdyts/carPt/relaxedPlans/output_plans.xml.gz "+
                                            arg+" "
                            };

                            parametricRuns.run(additionalLines, baseDir, jobName);
                            buffer.append(runCounter+"\t" + arg.replace(' ','\t') + newLine);
                        }
                    }
                }
            }
        }

        parametricRuns.writeNewOrAppendToRemoteFile(buffer, baseDir+"/runInfo.txt");
        parametricRuns.close();
    }

    public void writeNewOrAppendToRemoteFile(final StringBuilder buffer, final String file) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream w = new DataOutputStream(baos);
            w.writeBytes(buffer.toString());
            w.flush();

            sftp.put(new ByteArrayInputStream(baos.toByteArray()), file, ChannelSftp.APPEND);

            w.close();
            baos.close();
        } catch (SftpException e) {
            throw new RuntimeException("Data is not written/read. Reason : " + e);
        } catch (IOException e) {
            throw new RuntimeException("Data is not written/read. Reason : " + e);
        }
    }

    public void close(){
        session.disconnect();
        sftp.disconnect();
    }

    public void run(final String [] additionalLines, final String baseDir, final String jobName) {
        String [] commands = prepareCommands(additionalLines, baseDir, jobName);

        StringBuilder output = new StringBuilder();
        Arrays.stream(commands).forEach(cmd -> executeCommand(cmd, output));

        System.out.println(output.toString());
    }

    private void executeCommand( final String command, final StringBuilder output) {
        try {
            ChannelExec channel = (ChannelExec) session.openChannel("exec");

            channel.setCommand(command);
            InputStream stdout = channel.getInputStream();

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stdout, Charsets.UTF_8));
            channel.connect();
            String line = bufferedReader.readLine();
            while ( line != null) {
                output.append(line+"\n");
                line = bufferedReader.readLine();
            }
            channel.disconnect();
        } catch (JSchException e) {
            throw new RuntimeException("Aborting. Reason : " + e);
        } catch (IOException exx) {
            throw new RuntimeException("Data is not written/read. Reason : " + exx);
        }
    }

    private String [] prepareCommands(final String [] additionalLines, final String baseDir, final String jobName){

        String locationOfOutput = baseDir.endsWith("/") ? baseDir: baseDir+"/" ;
        locationOfOutput +=  jobName+"/";

        // create dir: if dir exits, an exception will be thrown.
        boolean isExists = false;
        try {
            sftp.lstat(locationOfOutput);
            isExists = true;
        } catch (SftpException e) {
            isExists = false;
        }

        if (! isExists) {
            try {
                sftp.mkdir(locationOfOutput);
            } catch (SftpException e) {
                throw new RuntimeException("Data is not written/read. Reason : " + e +". Check if the parent directory exists on remote. \n" +
                        "It will only create the job directories.");
            }
        }

        // location of file must be locale and then can be copied to remote.
        String jobScriptFileName = locationOfOutput+"/script_"+jobName+".sh";

        JobScriptWriter scriptWriter = new JobScriptWriter(userName);
        scriptWriter.appendCommands( jobName, locationOfOutput, additionalLines);
        scriptWriter.writeRemoteLocation(sftp, jobScriptFileName);

        return new String [] {
                "qstat -u "+userName,
                "qsub "+ this.jobParams+ " " + scriptWriter.getJobScript(),
                "qstat -u "+userName };
    }

    public void appendJobParameters(String jobParameters){
        this.jobParams += " "+jobParameters;
    }
}