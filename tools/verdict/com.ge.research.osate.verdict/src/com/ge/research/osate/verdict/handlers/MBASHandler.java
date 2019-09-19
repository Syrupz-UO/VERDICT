package com.ge.research.osate.verdict.handlers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.intro.IIntroPart;

import com.ge.research.osate.verdict.gui.BundlePreferences;
import com.ge.research.osate.verdict.gui.MBASReportGenerator;

/**
*
* Author: Paul Meng
* Date: Jun 12, 2019
*
*/

public class MBASHandler extends AbstractHandler {
	static final String SEP = File.separator;
	// Resource folder path
	static final String RESOURCE = VerdictHandlersUtils.EXTERN + SEP + "resource" + SEP;
	static final String GATESDIR = RESOURCE + "gates" + SEP;

	// STEM related command line arguments
	static final String GRAPHVIZ = "GraphVizPath";

	static final String STEM = "STEM";
	static final String STEMGRAPHDIR = "Graphs";
	static final String STEMOUTPUTDIR = "Output";
	static final String ECLIPSESADL = "ECLIPSE_SADL";
	static final String ECLIPSESADLPATH = System.getenv(ECLIPSESADL);
	static final String SADLFILE = "Run.sadl";
	static final String SADLWORKSPACEPATH = System.getenv("SADL_DEFAULT_WORKSPACE");
	static final String STEMPATH = VerdictHandlersUtils.EXTERN + SEP + STEM;
	static final String RUNSADLSCRIPTPATH = VerdictHandlersUtils.OSDIR + "RunSADL.sh";

	// Soteria++ related command line arguments
	static final String SOTERIA = VerdictHandlersUtils.OSDIR + "soteria_pp";

	// VDM2CSV
	static final String VDM2CSV = VerdictHandlersUtils.BINDIR + "vdm2csv.jar";

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		if (VerdictHandlersUtils.startRun()) {
			// Print on console
			IIntroPart introPart = PlatformUI.getWorkbench().getIntroManager().getIntro();
			PlatformUI.getWorkbench().getIntroManager().closeIntro(introPart);
			final IWorkbenchWindow iWindow = HandlerUtil.getActiveWorkbenchWindow(event);
			VerdictHandlersUtils.setPrintOnConsole("MBAS Output");
			Display mainThreadDisplay = Display.getCurrent();

			Thread mbasAnalysisThread = new Thread() {
				@Override
				public void run() {
					try {
						VerdictHandlersUtils.printGreeting();

						List<String> selection = VerdictHandlersUtils.getCurrentSelection(event);

//						String stemProjectDir = STEMPATH;
//						String soteriaOutputDir = stemProjectDir + SEP + "Output" + SEP + "Soteria_Output";

//						if (runBundle(selection.get(0), stemProjectDir)) {
//							// TODO display output in GUI
//							VerdictHandlersUtils.openSvgGraphsAndTxtInDir(soteriaOutputDir);
//						}

						String externStemDir = STEMPATH;

						deleteCsvFilesInDir(new File(externStemDir, "CSVData"));
						deleteCsvFilesInDir(new File(externStemDir, "Output"));

						if (runBundle(selection.get(0), externStemDir)) {
							String soteriaDir2 = invokeStem(new File(externStemDir, "CSVData").getAbsolutePath());
							if (VerdictHandlersUtils.isValidDir(soteriaDir2)) {
								String soteriaOut = invokeSoteria(soteriaDir2);
								if (soteriaOut != null) {
									// Run this code on the UI thread
									mainThreadDisplay.asyncExec(() -> {
										File applicableDefense = new File(soteriaOut, "ApplicableDefense.xml");
										File implProperty = new File(soteriaOut, "ImplProperty.xml");
										if (applicableDefense.exists() && implProperty.exists()) {
											new MBASReportGenerator(applicableDefense.getAbsolutePath(),
													implProperty.getAbsolutePath(), iWindow);
										} else {
											System.err.println("Error: No Soteria++ output generated!");
										}
									});
								}
							}
						}
					} finally {
						VerdictHandlersUtils.finishRun();
					}
				}
			};
			mbasAnalysisThread.start();
		}
		return null;
	}

	public static boolean runBundle(String inputPath, String stemProjectDir) {
		String bundleJar = BundlePreferences.getBundleJar();
		if (bundleJar.length() == 0) {
			System.out.println("Please set Verdict Bundle Jar path in Preferences");
			return false;
		}

		List<String> args = new ArrayList<>();

		args.add(VerdictHandlersUtils.JAVA);
		args.add(VerdictHandlersUtils.JAR);
		args.add(bundleJar);

		args.add("--aadl");
		args.add(inputPath);

		args.add("--mbas");
		args.add(stemProjectDir);

		int code = VerdictHandlersUtils.run(args.toArray(new String[args.size()]), null);

		return code == 0;
	}

	/**
	 *
	 * @param xmlFilePath
	 * 			input xml file path
	 * To run STEM via the script, we need to pass 4 parameters in the order as follows:
	 * 		ECLIPSESADLPATH: the path to the SADL eclipse binary
	 * 		STEM_PATH: the STEM project path
	 * 		SADL_WORKSPACE: the workspace path for SADL
	 * 		SADL_FILE: the SADL file to run with
	 * @return The path to the folder with generated csv files.
	 * 		   It will be null if encountering some runtime error.
	 */
	public String invokeStem(String csvDirPath) {
		if (!VerdictHandlersUtils.isValidExecutable(RUNSADLSCRIPTPATH)) {
			return null;
		}
		// Print header
		VerdictLogger.printHeader(STEM, csvDirPath);

		String dotPath = System.getenv(GRAPHVIZ);

		// Check if all the paths are valid
		if (ECLIPSESADLPATH == null) {
			VerdictLogger.severe("Please set a valid path for ECLIPSE_SADL in your system!");
			return null;
		} else {
			File sadlFile = new File(ECLIPSESADLPATH);

			if (!sadlFile.exists()) {
				VerdictLogger.info(ECLIPSESADL + ": " + ECLIPSESADLPATH + " does not exist!");
				return null;
			}
			VerdictLogger.info(ECLIPSESADL + " = " + ECLIPSESADLPATH);
		}

		if (!VerdictHandlersUtils.isValidDir(STEMPATH)) {
			VerdictLogger.severe("STEM path is invalid!" + STEMPATH);
			return null;
		} else {
			VerdictLogger.info(STEM + " = " + STEMPATH);
		}

		if (!VerdictHandlersUtils.isValidDir(dotPath)) {
			System.out.println(
					"Please set a valid path for GraphVizPath in your system to fully take advantage of features of STEM!");
		} else {
			VerdictLogger.info(GRAPHVIZ + " = " + dotPath);
		}

		// Run STEM
		String stemPathInWorkspace = SADLWORKSPACEPATH + SEP + STEM;
		String stemOutputCsvDirPath = stemPathInWorkspace + SEP + STEMOUTPUTDIR;
		String[] cmds = new String[] { RUNSADLSCRIPTPATH, ECLIPSESADLPATH, STEMPATH, SADLWORKSPACEPATH, SADLFILE };

		// Execute the commands
		if (VerdictHandlersUtils.runWithoutMsg(cmds, null) == 0) {
			// Open the graphs generated by STEM
			String stemGraphDir = stemPathInWorkspace + SEP + STEMGRAPHDIR;
			VerdictHandlersUtils.openSvgGraphsInDir(stemGraphDir);
		} else {
			stemOutputCsvDirPath = null;
		}

		return stemOutputCsvDirPath;
	}

	/**
	 * Invoke Soteria++ with a CSV directory
	 * */
	public String invokeSoteria(String soteriaCsvDirPath) {
		// Print header
		VerdictLogger.printHeader("Soteria++", soteriaCsvDirPath);

		String outputPath = soteriaCsvDirPath + SEP + "Soteria_Output" + SEP;

		// Copy the gates files to the output folder first
		copyGatesPngtoDstDir(new File(outputPath));

		// Run Soteria++
		String[] soteriaCmds = new String[] { SOTERIA, "-o", outputPath, soteriaCsvDirPath };

		if (VerdictHandlersUtils.run(soteriaCmds, outputPath) == 0) {
			// Open the graphs generated by Soteria++
//			VerdictHandlersUtils.openSvgGraphsAndTxtInDir(outputPath);
//			VerdictHandlersUtils.openSvgGraphsInDir(outputPath);

			return outputPath;
		} else {
			return null;
		}
	}

	/**
	 * Check if a directory has any csv files;
	 * Returns true, if it has at least one csv file; otherwise return false;
	 * */
	private static boolean hasCsvFiles(File dir) {
		if(dir != null && dir.exists()) {
			if(dir.isDirectory()) {
				for(File file : dir.listFiles()) {
					if (file.isFile()) {
						if (getFileExtension(file).equals("csv")) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	/**
	 * Delete all csv files in a folder
	 * */
	static void deleteCsvFilesInDir(File dir) {
		if (dir.exists()) {
			if (dir.isDirectory()) {
				for (File file : dir.listFiles()) {
					if (file.isFile()) {
						if (getFileExtension(file).equals("csv")) {
							file.delete();
						}
					}
				}
			} else {
				dir.mkdirs();
			}
		} else {
			dir.mkdirs();
		}
	}

	/**
	 * Get the extension of a file
	 * */
	private static String getFileExtension(File file) {
		String extension = "";

		try {
			if (file != null && file.exists()) {
				String name = file.getName();
				extension = name.substring(name.lastIndexOf(".") + 1).toLowerCase();
			}
		} catch (Exception e) {
			extension = "";
		}

		return extension;

	}

	/**
	 * Copy gates png from /resource/gates to a directory
	 * */
	public static void copyGatesPngtoDstDir(File dstDir) {
		File gateDir = new File(GATESDIR);

		// Create output directory for Soteria++
		if (!dstDir.exists() || !dstDir.isDirectory()) {
			if (!dstDir.mkdirs()) {
				System.out.println("Fail to create a directory:" + dstDir.getAbsolutePath());
				return;
			}
		}

		if (gateDir.exists() && gateDir.isDirectory()) {
			for (File srcFile : gateDir.listFiles()) {
				if (srcFile.isFile()) {

					File dstFile = new File(dstDir.getAbsolutePath() + SEP + srcFile.getName());

					if (!dstFile.exists()) {

						try {
							InputStream inStream = new FileInputStream(srcFile);
							OutputStream outStream = new FileOutputStream(dstFile);

							byte[] buffer = new byte[1024];

							int length;
							// copy the file content in bytes
							while ((length = inStream.read(buffer)) > 0) {
								outStream.write(buffer, 0, length);
							}

							inStream.close();
							outStream.close();
						} catch (FileNotFoundException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
		} else {
			VerdictLogger.warning("Warning: Pictures for gates are not located at " + GATESDIR
					+ ", graphs generated by Soteria++ may look ugly!");
		}
	}

}
