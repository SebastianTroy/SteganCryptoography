package encryption;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * This class allows the user to select a .jpg or .png image file, it will take the image filename and encode it as a message while renaming the image to
 * whatever was previously encoded in the image.
 * <p>
 * It does this by converting the RGB values of a pixel into binary, if the value is even, it is a 1, if it is odd, the value is 0 however the information is
 * encoded into 4*4 packets which are read by a complex and randomly (in a specific way from a seed) generated neural network.
 * <p>
 * The reason for this complexity is an attempt to mask the fact that data is hidden within the image, my hope is that statistical tests designed to detect the
 * non-random distribution of data are completely fooled into finding a completely random distribution. Due to the complexity of the neural network there are
 * many different combinations (approximately (2^16) / 28 == 2340) available for each character of the alphabet, plus spaces and a terminating character.
 * <p>
 * If it is run using the command line a single variable, an integer, can be passed in, this will be used as the seed for generating the network so it has to be
 * used when encrypting and decrypting the message. <strong>WARNING</strong> If you attempt to decrypt the message with the wrong seed it will be lost forever!
 * 
 * @author Sebastian Troy
 */
public class Encryption
	{
		/**
		 * This is the file representing the image that is currently being worked with.
		 */
		private static File imageFile;

		/**
		 * This is initialised to the directory that the program is run from during the initialisation of the program.
		 */
		private static File currentDirectory;

		/**
		 * This is the {@link Network} that will be used to read and write encoded information from and to the chosen image file.
		 */
		private static Network n;

		/**
		 * The opening for the program. The directory in which the program was run from is located and, if specified, the seed is set.
		 * 
		 * @param args
		 *            - if a single number is present, it is used to seed the random number generator
		 */
		public static void main(String[] args)
			{
				Path currentRelativePath = Paths.get("");
				currentDirectory = new File(currentRelativePath.toAbsolutePath().toString());

				// Default seed chosen for extremely low standard deviation making message detection harder due to uniform spread of data
				final int seed = Integer.parseInt(System.getProperty("seed", "1198662804"));
				final String mode = System.getProperty("mode", "SWAP_MESSAGES");

				Network.LEEWAY = Float.parseFloat(System.getProperty("leeway", "0.07"));
				Network.LEVELS = Integer.parseInt(System.getProperty("levels", "3"));

				n = new Network(seed);
				
				if (mode.equals("dictionary"))
					{
						/* Iterate through all possible startingNodeStates for the network and print the resulting character sequence */
						printDictionary();
					}
				else if (mode.equals("stats"))
					{
						/* Iterate through all possible startingNodeStates and for the resulting dictionary */
						printStats();
					}
				else if (mode.equals("optimise"))
					{
						/* Get the optimum Network variables for the best balanced network, i.e. lowest standardDeviation */
						getOptimumSeed();
					}
				else if (mode.equals("graph"))
					{
						/* Get a graph of the Standard Deviations across a range of leeways, don't forget you can also set the number of LEVELS in the Network */
						float minLeeway = Float.parseFloat(System.getProperty("minleeway", "0"));
						float maxLeeway = Float.parseFloat(System.getProperty("maxleeway", "0.4"));
						getGraphOfStats(minLeeway, maxLeeway);
					}
				else if (mode.equals("SWAP_MESSAGES"))
					{
						// Check if user set a seed, otherwise use default value
						new Encryption(seed);
					}
				else
					{
						JOptionPane
								.showMessageDialog(
										null,
										"Invalid mode specified try: \n -Doptimise get the optimum seed for the current network parameters \n -Dstats to see stats on the network \n -Ddictionary to print a complete set of character representations",
										"Error", JOptionPane.ERROR_MESSAGE);
					}
			}

		/**
		 * The only Constructor for the {@link Encryption} class.
		 * <p>
		 * The user is asked to select an image file using a {@link JFileChooser}, then that image has any internal message decrypted into its new filename and
		 * its old filename encrypted into it as a message.
		 */
		private Encryption(int seed)
			{
				boolean goAgain = true;

				// Go until the user does not chose an image
				while (goAgain)
					{
						// Code to load the desired
						JFileChooser chooser = new JFileChooser(currentDirectory);
						FileNameExtensionFilter filter = new FileNameExtensionFilter("Certain Image types only", "jpg", "png");
						chooser.setFileFilter(filter);

						// if the user chose an image
						int returnVal = chooser.showOpenDialog(null);
						if (returnVal == JFileChooser.APPROVE_OPTION)
							{
								try
									{
										// Get the user to select an image file
										imageFile = chooser.getSelectedFile();
										BufferedImage img = ImageIO.read(imageFile);

										/*
										 * Swap the filename with the encrypted message in the image, note that gibberish will be extracted from any file with
										 * no message previously encoded into it
										 */
										swapInformation(img);
									}
								catch (Exception e)
									{
										JOptionPane.showMessageDialog(null, "Sorry but that image doesn't work!", "Error", JOptionPane.ERROR_MESSAGE);
										e.printStackTrace();
									}
							}
						// else quit the program
						else
							goAgain = false;
					}
			}

		/**
		 * This Method Treats the file name of the image as one message and any information encoded within the image as the second, it then swaps these messages
		 * around, encoding the file name into the image and renaming the image to the previously encoded message.
		 * 
		 * @param img
		 *            - The image file that we want to extract a message from and hide a message within.
		 * @param newFileName
		 *            - What the file will be renamed to (Whatever was previously stored within the image file)
		 */
		private final void swapInformation(BufferedImage img)
			{
				/* Extract the message currently encoded within the image (will be gibberish if this is the first time this program has been run on an image). */
				String newFileName = getCurrentlyEncryptedMessage(img);

				// Then wipe the old message from the image, ready for the new image to be saved
				cleanImage(img);

				/* Get the message from the filename, we add the terminating character to it so that the Network knows when to stop decoding when it tries to */
				String message = "x" + imageFile.getName().substring(0, imageFile.getName().indexOf('.')) + '{';

				// The index of the current character that we are writing to in the image
				int xIndex = 0, yIndex = 0;

				// represents the data to be encoded into the character
				boolean[] startNodeStates = { false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false };

				// While there is still message to be encoded (it will be shortened as the encoding happens)
				do
					{
						// The next character we want to encode
						char charToEncode = message.charAt(0);
						Character.toLowerCase(charToEncode);

						// If the character is a space, '{' or an alphabetic character then encode it, otherwise skip it
						if (charToEncode == ' ' || ((int) charToEncode >= 97 && (int) charToEncode <= 123))
							{

								/*
								 * while our data doesn't represent our desired character, randomise it until it does... (it works fast enough for short
								 * messages, perhaps making a dictionary beforehand would be a better choice for much longer messages?)
								 */
								while (n.getLetter(startNodeStates) != charToEncode)
									for (int i = 0; i < 16; i++)
										startNodeStates[i] = n.r.nextBoolean();

								// Overlay the data for the character in the correct spot
								for (int x = 0; x < 4; x++)
									for (int y = 0; y < 4; y++)
										if (startNodeStates[x + (y * 4)])
											img.setRGB((xIndex * 4) + x, (yIndex * 4) + y, img.getRGB((xIndex * 4) + x, (yIndex * 4) + y) + 1);

								// move onto the next space in the image to encode the next letter
								xIndex++;
								if (xIndex >= (int) (img.getWidth() / 4))
									{
										xIndex = 0;
										yIndex++;
										// if we have run out of room in the image, exit this loop
										if (yIndex >= (int) (img.getHeight() / 4))
											{
												System.out.println("Sorry but your message was too long for the image the following was lost: \n -" + message.substring(0, message.lastIndexOf('{')));
												message = "{";
											}
									}
							}

						// If there is still message left to encode remove the first letter so we know we are on the next one
						message = message.substring(1);
					}
				while (message.length() > 0);

				// once the entire message has been encoded, random data is used to fill the rest of the image
				fillImageWithRandomData(img, xIndex, yIndex);

				// Save the file as an image called img.jpg
				try
					{
						// write the new image data to the newly renamed image
						ImageIO.write(img, "png", imageFile);
						// Get the file extension
						String fileExtension = imageFile.getName().substring(imageFile.getName().indexOf('.'), imageFile.getName().length());
						// create a new file with a new name
						File renamedImage = new File(imageFile.getParent() + "\\" + newFileName + fileExtension);
						// replace the old image
						imageFile.renameTo(renamedImage);
					}
				catch (Exception e)
					{
						e.printStackTrace();
					}
			}

		/**
		 * This method extracts data encrypted into an image using the encrypt method. It assumes the default seed was used to generate the neural network
		 * unless another seed is explicitly supplied in the command line.
		 * 
		 * @param img
		 *            - A BufferedImage containing encrypted data
		 * 
		 */
		private final String getCurrentlyEncryptedMessage(BufferedImage img)
			{
				String message = "";

				// represents the data extracted, will be passed through neural network
				boolean[] startNodeStates = new boolean[16];

				// for each character of data that can be stored (4*4 square)
				for (int y = 0; y < (int) (img.getHeight() / 4); y++)
					for (int x = 0; x < (int) (img.getWidth() / 4); x++)
						{
							// for each pixel in that character
							for (int x2 = 0; x2 < 4; x2++)
								for (int y2 = 0; y2 < 4; y2++)
									/* Get the starting state for it's corresponding node in the first level of nodes in the network */
									startNodeStates[x2 + (y2 * 4)] = (img.getRGB((x * 4) + x2, (y * 4) + y2) % 2 != 0);

							// Turn the data into the next character
							message = message + n.getLetter(startNodeStates);
						}

				// trim the message from the start to the first instance of the terminating character
				if (message.indexOf('{') > 1)
					message = message.substring(1, message.indexOf('{'));

				return message;
			}

		/**
		 * This method cycles through the images RGB values and makes sure that they are all even numbers. It does so in the same pattern that data will be
		 * inserted in to avoid leaving the unusable edges of the picture with totally uniform data.
		 * 
		 * @param img
		 *            - The image to be set to a non-encoded state
		 * @return The same image with an imperceptable colour shift that has no extra encoded information
		 */
		private void cleanImage(BufferedImage img)
			{
				int maxX = ((int) (img.getWidth() / 4)) * 4;
				int maxY = ((int) (img.getHeight() / 4)) * 4;

				// for each pixel that could ever be part of a message
				for (int x = 0; x < maxX; x++)
					for (int y = 0; y < maxY; y++)
						// if RGB value isn't even, make it even
						if (img.getRGB(x, y) % 2 != 0)
							img.setRGB(x, y, img.getRGB(x, y) - 1);
			}

		/**
		 * Fills the image with random data, an x or y index can be chosen so that an encrypted message already within the image isn't overwritten.
		 * 
		 * @param img
		 *            - the image to be filled with random data
		 * @param startX
		 *            - not the absolute x pixel position but the index of the character of data to start at
		 * @param startY
		 *            - not the absolute y pixel position but the index of the character of data to start at
		 */
		private void fillImageWithRandomData(BufferedImage img, int startX, int startY)
			{
				final Random r = new Random();

				// for each character of data that can be stored (4*4 square)
				for (int x = startX; x < (int) (img.getWidth() / 4); x++)
					for (int y = startY; y < (int) (img.getHeight() / 4); y++)
						{
							// for each bit of data that can be stored for that character
							for (int x2 = 0; x2 < 4; x2++)
								for (int y2 = 0; y2 < 4; y2++)
									if (r.nextBoolean())
										// Overwrite the bit of data with an odd value
										img.setRGB((x * 4) + x2, (y * 4) + y2, img.getRGB((x * 4) + x2, (y * 4) + y2) + 1);
							/* it is assumed that the image has been "cleaned" prior to the addition of random data so all RGB values are even */
						}
			}

		private static class Network
			{
				/**
				 * One for each character we want to be able to recognise
				 */
				static final int NODES_PER_LEVEL = 28;
				/**
				 * The number of floats stored to represent a single Node
				 * <p>
				 * { Node's {@link #GOAL}, Node's {@link #STATE}, weight of connection to node 0, weight of connection to node 1... }
				 */
				static final int DATA_PER_NODE = NODES_PER_LEVEL + 2;
				/**
				 * The number of levels that the {@link Network} has.
				 */
				static int LEVELS;

				/**
				 * The state that the node needs to reach in order to be triggered (give or take the {@link #LEEWAY})
				 */
				static final int GOAL = 0;
				/**
				 * The current state of the Node
				 */
				static final int STATE = 1;

				/**
				 * If a Node's state is within the range of (its goal +/- {@link Network#LEEWAY}) then it is triggered
				 */
				static float LEEWAY;

				final Random r;

				/**
				 * float[<i>The data for a Node</i>][<i>Times the number of nodes in a level</i>][<i>Times the number of levels in the network</i>]
				 * <p>
				 * The data for a node: { Nodes goal, Nodes State, weight of connection to node 0, weight of connection to node 1... }
				 * <ul>
				 * <li>A nodes state is the summation of all of the weights of the connections from triggered nodes in the layer above</li>
				 * <li>A nodes goal is the state it needs to be triggered itself</li>
				 * </ul>
				 * <p>
				 */
				float[][][] network;

				/**
				 * Initialises a {@link Network} and sets the node weights to random values, using a known seed to ensure it is constructed identically each
				 * time.
				 * <p>
				 * {@link Network#LEEWAY} and {@link Network#LEVELS} can be set by command line parameters (-Dleeway=<i>a float</i> & -Dlevels=<i>an
				 * integer</i>)
				 */
				private Network(int seed)
					{
						network = new float[DATA_PER_NODE][NODES_PER_LEVEL][LEVELS];

						// seed our random number generator to ensure we can rebuild the network
						r = new java.util.Random(seed);

						for (int x = 0; x < DATA_PER_NODE; x++)
							for (int y = 0; y < NODES_PER_LEVEL; y++)
								for (int z = 0; z < LEVELS; z++)
									if (x == STATE)
										network[x][y][z] = 0;
									else
										network[x][y][z] = r.nextFloat() - 0.5f;
					}

				/**
				 * For a 16 long set of booleans (representing a 4*4 grid) feeds the information into the network and translates it into a letter of the
				 * alphabet or the terminating character '{'
				 * 
				 * @param firstLevelNodeStates
				 *            - each state represents whether a node in the first level of the network will fire.
				 * @return - an non-capital alphabet character or "{" which is the termination character.
				 */
				private char getLetter(boolean[] firstLevelNodeStates)
					{
						// Set up the starting nodes so only the relevant ones fire
						for (int i = 0; i < firstLevelNodeStates.length; i++)
							if (firstLevelNodeStates[i]) // If Node needs to fire
								// Make its STATE == its GOAL to guarantee firing
								network[STATE][i][0] += network[GOAL][i][0];

						/*
						 * For each level (except the last), for each node in that level: Check if that node has fired.
						 * 
						 * If it has, for each connection, update the connected nodes STATE by adding the connections value to it
						 */
						for (int z = 0; z < LEVELS - 1; z++)
							for (int y = 0; y < NODES_PER_LEVEL; y++)
								if (getNodeDifference(y, z) < LEEWAY)
									for (int x = 2; x < DATA_PER_NODE; x++)
										network[STATE][x - 2][z + 1] += network[x][y][z];

						// Find which of the nodes in the final level is closest to its goal value
						int closestNodeNum = 0;
						float closetsNodeValue = getNodeDifference(0, LEVELS - 1);

						/*
						 * for each node, check if its GOAL more closely matches its STATE than the previous best, if it does, replace closestNodeNum with the
						 * nodes own index
						 */
						for (int i = 0; i < NODES_PER_LEVEL; i++)
							if (getNodeDifference(i, LEVELS - 1) < closetsNodeValue)
								{
									closestNodeNum = i;
									closetsNodeValue = getNodeDifference(i, LEVELS - 1);
								}

						// Reset the network so it is ready to be used again
						resetStates();

						/*
						 * Get the character that we want to return ascii 'a' -> '{' = 97 -> 123
						 */
						char charToReturn = new Character((char) (closestNodeNum + 97));

						if (closestNodeNum == NODES_PER_LEVEL - 1)
							charToReturn = ' ';

						return charToReturn;
					}

				/**
				 * Calculates how close a node's STATE is to its GOAL
				 * 
				 * @param y
				 *            the number of the node
				 * @param z
				 *            the LEVEL that the node is in
				 * @return the absolute difference between the node's STATE and GOAL
				 */
				private float getNodeDifference(int y, int z)
					{
						return Math.abs((Math.abs(network[GOAL][y][z]) - Math.abs(network[STATE][y][z])));
					}

				/**
				 * Resets the network so that all node states are 0, ready for the next use.
				 */
				private void resetStates()
					{
						for (int y = 0; y < NODES_PER_LEVEL; y++)
							for (int z = 0; z < LEVELS; z++)
								network[STATE][y][z] = 0;
					}
			}

		/*
		 * The following methods were used for debugging & fine tuning the network
		 */

		/**
		 * Cycles through every combination of startingNodeStates in order and prints out the
		 * 
		 * @param seed
		 *            - the seed used to create the Network
		 */
		private final static void printDictionary()
			{
				boolean[] letterInformation = { false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false };
				boolean repeat = true;

				int counter = 0;

				while (repeat)
					{
						System.out.print(n.getLetter(letterInformation));
						counter++;
						if (counter > 200)
							{
								counter = 0;
								System.out.println();
							}

						repeat = letterInformationIncrement(letterInformation);
					}
				System.out.println();
			}

		/**
		 * Print to console the stats for a single Network.
		 * <p>
		 * Each character can be represented by ~2340 different combinations of the startingNodeStates, the standard deviation represents the variability in the
		 * actual numbers that represent each. The lower the number the better with ~400 being good and with 100 representing true random assignment.
		 * 
		 * @param seed
		 *            - the seed used to create the Network
		 */
		private final static void printStats()
			{
				boolean[] letterInformation = { false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false };
				boolean repeat = true;

				int[] charFrequency = new int[Network.NODES_PER_LEVEL];

				while (repeat)
					{
						int character = (int) (n.getLetter(letterInformation) - 97);
						if (character == -65)
							character = Network.NODES_PER_LEVEL - 1;

						charFrequency[character]++;

						repeat = letterInformationIncrement(letterInformation);
					}
				double mean = getMean(charFrequency);
				double total = mean * charFrequency.length;
				double stdv = getStandardDeviation(charFrequency, mean);

				System.out.println("Standard Deviation: " + stdv + " (aim = ~100)");

				System.out.print("{ " + (int) (charFrequency[0] / total * 100));
				for (int i = 1; i < charFrequency.length; i++)
					System.out.print(", " + (int) (charFrequency[i] / total * 100));
				System.out.println(" }");
			}

		private static final void getOptimumSeed()
			{
				Random r = new Random();

				int bestSeed = 54343;
				double bestStdv = 1000000;

				boolean goAgain = true;

				// For each pair of spaces in our graph data
				while (goAgain)
					{
						// Chose a random seed and test the network's standard deviation
						int newSeed = r.nextInt();
						n = new Network(newSeed);

						// create the memory for our startingNodeStates
						boolean[] letterInformation = { false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false };
						boolean repeat = true;

						// set aside memory to record the frequency our network returns each character
						int[] charFrequency = new int[Network.NODES_PER_LEVEL];

						// for each and every combination of staryingNodeStates (2^16)
						while (repeat)
							{
								int character = (int) (n.getLetter(letterInformation) - 97);
								if (character == -65)
									character = Network.NODES_PER_LEVEL - 1;

								charFrequency[character]++;

								// increment the startingNodeStates
								repeat = letterInformationIncrement(letterInformation);
							}

						// Calculate the standard deviation
						double stdv = getStandardDeviation(charFrequency, getMean(charFrequency));

						// If a better solution is found, notify user and ask if they want to continue the search
						if (stdv < bestStdv)
							{								
								bestSeed = newSeed;
								bestStdv = stdv;

								goAgain = JOptionPane.showConfirmDialog(null, "Found a better seed: " + bestSeed + ", Standard Deviation: \n " + bestStdv + "   (< 400 = very good)", "Keep Searching?", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION;
							
								System.out.println();
								System.out.println("Seed: " + bestSeed + "     for: LEEWAY == " + Network.LEEWAY + " | LEVELS == " + Network.LEVELS);
							}
						else
							{
								System.out.print(".");
								System.out.flush();
							}
					}
				System.out.println("Best Seed: " + bestSeed + "     for: LEEWAY == " + Network.LEEWAY + " | LEVELS == " + Network.LEVELS);
			}

		/**
		 * Get a graph of the Standard Deviations across a range of leeways.
		 * 
		 * @param minLeeway
		 *            - The minimum LEEWAY value on the x axis.
		 * @param maxLeeway
		 *            - the maximum LEEWAY value on the x axis.
		 */
		private static final void getGraphOfStats(float minLeeway, float maxLeeway)
			{
				Random r = new Random();

				// Memory for our graph's data points {x1, y1, x2, y2, x3...}
				float[] data = new float[500];
				// The current index we have got to while filling our data
				int index = 0;

				// For each pair of spaces in our graph data
				for (int i = 0; i < data.length / 2; i++)
					{
						// Create a network with a LEEWAY randomly selected from our range.
						Network.LEEWAY = ((r.nextFloat() * (maxLeeway - minLeeway)) + minLeeway);
						n = new Network(r.nextInt());

						// create the memory for our startingNodeStates
						boolean[] letterInformation = { false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false };
						boolean repeat = true;

						// set aside memory to record the frequency our network returns each character
						int[] charFrequency = new int[Network.NODES_PER_LEVEL];

						// for each and every combination of staryingNodeStates (2^16)
						while (repeat)
							{
								int character = (int) (n.getLetter(letterInformation) - 97);
								if (character == -65)
									character = Network.NODES_PER_LEVEL - 1;

								charFrequency[character]++;

								repeat = letterInformationIncrement(letterInformation);
							}

						// Calculate the standard deviation
						double stdv = getStandardDeviation(charFrequency, getMean(charFrequency));

						// Add the data to our data points
						data[index] = Network.LEEWAY;
						data[index + 1] = Math.round(stdv);

						// Move onto the next data point
						index += 2;
					}

				// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
				// Draw the graph once we've collected all of the data points
				// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

				// First find the upper and lower bounds of our axes
				float lowX = minLeeway;
				float lowY = data[1];
				float highX = maxLeeway;
				float highY = data[1];

				// Cycle through y values to find the range of the axis
				for (int i = 1; i < data.length; i += 2)
					if (data[i] < lowY)
						lowY = data[i];
					else if (data[i] > highY)
						highY = data[i];

				// set the dimensions of the image to draw the graph to
				int width = 1000;
				int height = 1000;
				int margin = 40;
				int pointSize = 10;

				// Create a BufferedImage to draw our graph onto
				BufferedImage graphImage = new BufferedImage(width + 20, height + 20, BufferedImage.TYPE_INT_ARGB);
				Graphics g = graphImage.getGraphics();

				// Clear the image
				g.setColor(Color.WHITE);
				g.fillRect(0, 0, width, height);

				// Draw axes
				g.setColor(Color.BLACK);
				g.drawLine(margin, height - margin, width, height - margin);// x
				g.drawString("" + getToDecimalPlaces(lowX, 8), margin, height - 8);
				g.drawString("" + getToDecimalPlaces(highX, 8), width - 60, height - 8);
				g.drawString("Leeway", width / 2, height - 8);

				g.drawLine(margin, height - margin, margin, 0);// y
				g.drawString("" + (int) lowY, 2, height - margin);
				g.drawString("" + (int) highY, 2, 12);
				g.drawString("Std Deviation", 2, height / 2);

				// Draw the points
				g.setColor(new Color(0, 0, 0, 50));
				for (int i = 0; i < data.length; i += 2)
					{
						int x = (int) ((data[i] - lowX) * ((width - margin) / (highX - lowX))) + margin - (pointSize / 2);
						int y = (height - margin) - (int) ((data[i + 1] - lowY) * ((height - margin) / (highY - lowY))) - (pointSize / 2);
						g.fillRect(x, y, pointSize, pointSize);
					}

				// Save our graph as an image
				try
					{
						imageFile = new File(System.getProperty("user.home") + "/Desktop" + "/graph_" + minLeeway + "_" + maxLeeway + ".png");
						imageFile.createNewFile();
						// write the new image data to the newly renamed image
						ImageIO.write(graphImage, "png", imageFile);
					}
				catch (Exception e)
					{
						e.printStackTrace();
					}

				System.out.println("DONE");
				JOptionPane.showMessageDialog(null, "Finished, find the Graph on your desktop named: \n" + System.getProperty("user.home") + "/Desktop" + "/graph_" + minLeeway + "_" + maxLeeway
						+ ".png", "Complete", JOptionPane.INFORMATION_MESSAGE);
				return;
			}

		/*
		 * The following methods were used for debugging & fine tuning the network
		 */

		/**
		 * Basically treats the startingNodeStates as a binary number and increments it.
		 * 
		 * @param states
		 *            the startingNodeStates that can be pssed through the network in order
		 * @return
		 */
		private final static boolean letterInformationIncrement(boolean[] states)
			{
				for (int i = 0; i < states.length; i++)
					{
						states[i] = !states[i];
						if (i == states.length - 1 && !states[i])
							{
								return false;
							}
						else if (states[i])
							return true;
					}
				return true;
			}

		/**
		 * @param char_frequencies
		 *            - The list of values you want the average for.
		 * @return - The average value of a list of integers.
		 */
		private static final double getMean(int[] char_frequencies)
			{
				double mean = 0;
				// Add all of our character frequencies up
				for (int i = 0; i < char_frequencies.length; i++)
					mean += char_frequencies[i];

				// Return the total divided by the number of character frequencies
				return mean /= char_frequencies.length;
			}

		/**
		 * @param charFrequencies
		 *            - the list of values you want the standard deviation for.
		 * @param mean
		 *            - see {@link #getMean(int[])}
		 * @return - The standard deviation for the list of values.
		 */
		private static final double getStandardDeviation(int[] charFrequencies, double mean)
			{
				double stdv = 0;
				for (int i = 0; i < charFrequencies.length; i++)
					stdv += (charFrequencies[i] - mean) * (charFrequencies[i] - mean);

				return Math.sqrt(stdv / charFrequencies.length);
			}

		/**
		 * Simply cuts off the decimal places after a desired point. Used to make the graph less cluttered with long decimal numbers.
		 * <p>
		 * <strong>Warning: Java only displays decimal places to the last non-zero digit</strong>
		 * 
		 * @param number
		 *            - The number that you want shortened
		 * @param decimalPlaces
		 *            - The number of decimal places you want it shortened to
		 * @return - The original number but shortened to a set number of decimal places
		 */
		private static double getToDecimalPlaces(double number, int decimalPlaces)
			{
				return (double) ((int) (number * (10 * decimalPlaces))) / (10 * decimalPlaces);
			}
	}