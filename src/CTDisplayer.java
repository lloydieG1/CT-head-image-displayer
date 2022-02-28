
/*
CS-255 Getting started code for the assignment
I do not give you permission to post this code online
Do not post your solution online
Do not copy code
Do not use JavaFX functions or other libraries to do the main parts of the assignment:
	1. Creating a resized image (you must implement nearest neighbour and bilinear interpolation yourself
	2. Gamma correcting the image
	3. Creating the image which has all the thumbnails and event handling to change the larger image
All of those functions must be written by yourself
You may use libraries / IDE to achieve a better GUI
*/
import java.io.*;
import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Toggle;
//import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;  
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class CTDisplayer extends Application {
	short cthead[][][]; //store the 3D volume data set
	float grey[][][]; //store the 3D volume data set converted to 0-1 ready to copy to the image
	float gammaLookup[][][] = new float[256][256][256]; //array same size as grey
	short min, max; //min/max value in the 3D volume data set
	int currentSlice = 76;
	int thumbnailSize = 70;
	int slicePadding = 5; //how many pixels padding each thumbnail
	int rows = 9;
	ImageView TopView;

    @Override
    public void start(Stage stage) throws FileNotFoundException {
		stage.setTitle("CThead Viewer");
		
		try {
			ReadData();
		} catch (IOException e) {
			System.out.println("Error: The CThead file is not in the working directory");
			System.out.println("Working Directory = " + System.getProperty("user.dir"));
			return;
		}
		
		//initialise gamma lookup table to have default values for all slices (identical to grey array)
		for (int s = 0; s < grey.length; s++) {
			gamma(s, 1);
		}
		
		//int width=1024, height=1024; //maximum size of the image
		//We need 3 things to see an image
		//1. We need to create the image
		Image top_image=GetSlice(currentSlice); //go get the slice image
		//2. We create a view of that image
		TopView = new ImageView(top_image); //and then see 3. below

		//Create the simple GUI
		final ToggleGroup group = new ToggleGroup();

		RadioButton rb1 = new RadioButton("Nearest neighbour");
		rb1.setToggleGroup(group);
		rb1.setSelected(true);

		RadioButton rb2 = new RadioButton("Bilinear");
		rb2.setToggleGroup(group);

		Slider szslider = new Slider(32, 1024, 256);
		
		Slider gamma_slider = new Slider(.1, 4, 1);

		//Radio button changes between nearest neighbour and bilinear
		group.selectedToggleProperty().addListener(new ChangeListener<Toggle>() {
			public void changed(ObservableValue<? extends Toggle> ob, Toggle o, Toggle n) {
 
				if (rb1.isSelected()) {
					System.out.println("Radio button 1 clicked");
				} else if (rb2.isSelected()) {
					System.out.println("Radio button 2 clicked");
				}
            }
        });
		
		//Size of main image changes (slider)
		szslider.valueProperty().addListener(new ChangeListener<Number>() { 
			public void changed(ObservableValue <? extends Number >  
					observable, Number oldValue, Number newValue) { 

				System.out.println(oldValue.intValue());
				System.out.println(newValue.intValue());
				//Here's the basic code you need to update an image
				TopView.setImage(null); //clear the old image
				Image newImage = null;
				if (rb1.isSelected()) {
					newImage = nearestNeighbor(currentSlice, newValue.intValue()); //slice scaled using nearest neighbor
				} else if (rb2.isSelected()) {
					newImage = bilinear(currentSlice, newValue.intValue()); //slice scaled using bilinear
				}
				TopView.setImage(newImage); //Update the GUI so the new image is displayed
            } 
        });
		
		//Gamma value changes
		gamma_slider.valueProperty().addListener(new ChangeListener<Number>() { 
			public void changed(ObservableValue <? extends Number >  
						observable, Number oldValue, Number newValue) { 
				//print new gamma value
				System.out.println(newValue.doubleValue());
				//change intensity values in lookup table using new gamma value
				gamma(currentSlice, newValue.doubleValue());

				//update the current image with new gamma values and correct scaling
				TopView.setImage(null); //clear the old image
				Image newImage = null;
				if (rb1.isSelected()) {
					newImage = nearestNeighbor(currentSlice, (float) szslider.getValue()); //slice scaled using bilinear
				} else if (rb2.isSelected()) {
					newImage = bilinear(currentSlice, (float) szslider.getValue()); //slice scaled using nearest neighbor
				}
				TopView.setImage(newImage); //Update the GUI so the new image is displayed
			}
		});
		
		VBox root = new VBox();

		//Add all the GUI elements
        //3. (referring to the 3 things we need to display an image)
      	//we need to add it to the layout
		root.getChildren().addAll(rb1, rb2, gamma_slider,szslider, TopView);

		//Display to user
        Scene scene = new Scene(root, 1024, 768);
        stage.setScene(scene);
        stage.show();
        
        ThumbWindow(scene.getX()+200, scene.getY()+200);
    }
    

	//Function to read in the cthead data set
	public void ReadData() throws IOException {
		//File name is hardcoded here - much nicer to have a dialog to select it and capture the size from the user
		File file = new File("CThead");
		//Read the data quickly via a buffer (in C++ you can just do a single fread - I couldn't find the equivalent in Java)
		DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
		
		int i, j, k; //loop through the 3D data set
		
		min=Short.MAX_VALUE; max=Short.MIN_VALUE; //set to extreme values
		short read; //value read in
		int b1, b2; //data is wrong Endian (check wikipedia) for Java so we need to swap the bytes around
		
		cthead = new short[113][256][256]; //allocate the memory - note this is fixed for this data set
		grey= new float[113][256][256];
		//loop through the data reading it in
		for (k=0; k<113; k++) {
			for (j=0; j<256; j++) {
				for (i=0; i<256; i++) {
					//because the Endianess is wrong, it needs to be read byte at a time and swapped
					b1=((int)in.readByte()) & 0xff; //the 0xff is because Java does not have unsigned types (C++ is so much easier!)
					b2=((int)in.readByte()) & 0xff; //the 0xff is because Java does not have unsigned types (C++ is so much easier!)
					read=(short)((b2<<8) | b1); //and swizzle the bytes around
					if (read<min) min=read; //update the minimum
					if (read>max) max=read; //update the maximum
					cthead[k][j][i]=read; //put the short into memory (in C++ you can replace all this code with one fread)
				}
			}
		}
		System.out.println(min+" "+max); //diagnostic - for CThead this should be -1117, 2248
		//(i.e. there are 3366 levels of grey, and now we will normalise them to 0-1 for display purposes
		//I know the min and max already, so I could have put the normalisation in the above loop, but I put it separate here
		for (k=0; k<113; k++) {
			for (j=0; j<256; j++) {
				for (i=0; i<256; i++) {
					grey[k][j][i]=((float) cthead[k][j][i]-(float) min)/((float) max-(float) min);
				}
			}
		}
		
	}
	
	//Gets an image from a slice that is 256 by 256 
	public Image GetSlice(int slice) {
		WritableImage image = new WritableImage(256, 256);
		//Find the width and height of the image to be process
		int width = (int)image.getWidth();
        int height = (int)image.getHeight();
        float val;

		//Get an interface to write to that image memory
		PixelWriter image_writer = image.getPixelWriter();

		//Iterate over all pixels
		for(int y = 0; y < height; y++) {
			for(int x = 0; x < width; x++) {
				//For each pixel, get the colour from the current cthead slice
				val=grey[slice][y][x];
				Color color=Color.color(val,val,val);
				
				//Apply the new colour
				image_writer.setColor(x, y, color);
			}
		}
		return image;
	}
	
	public void gamma(int slice, double gammaVal) {
		int srcWidth = 256;
		int srcHeight = 256;
		
		float intensity;
		double val;
		
		//for every pixel in grey
		for (int y = 0; y < srcHeight; y++) {
			for (int x = 0; x < srcWidth; x++) {
				val = grey[slice][y][x];
				//calculate intensity using: Intensity = Value^1/gamma
				intensity = (float) Math.pow(val, 1/gammaVal); 
				//set value in lookup table to intensity
				gammaLookup[slice][y][x] = intensity;
			}
		}
	}
	
	//Scales an image to the desired image size using a nearest neighbor algorithm
	public Image bilinear(int slice, float pixelScale) {
		//scale factor relative to the default cubic slice size of 256
		int srcWidth = 256;
		int srcHeight = 256;
		int destWidth = (int) pixelScale;
		int destHeight = (int) pixelScale;

		float topLeftColor;
		float topRightColor;
		float bottomLeftColor;
		float bottomRightColor;
		
		float xTopInterpolation;
		float xBottomInterpolation;
		float yFinalInterpolation;
		
		float leftRatio = 0;
		float rightRatio = 0;
		float topRatio = 0;
		float bottomRatio = 0;
		
		WritableImage scaledImage = new WritableImage(destWidth, destHeight);
		PixelWriter image_writer = scaledImage.getPixelWriter();
		
		//for every pixel in the new image
		for (int y = 0; y < destHeight; y++) {
			for (int x = 0; x < destWidth; x++) {
				float currentY = y;
				float currentX = x;
				
				/* calculate the greyscale value in the scaled image based on a ratio of the old image.
				 * 
				 * also the math.min bounds the x and y to be no greater than index 244
				 */
				float srcY = Math.min(srcHeight * (currentY/destHeight), srcHeight - 2); 
				float srcX =  Math.min(srcWidth * (currentX/destWidth), srcWidth - 2);
				
				int closestY = (int) Math.min(Math.floor(srcY), srcHeight - 2); 
				int closestX = (int) Math.min(Math.floor(srcX), srcWidth - 2);
				
				//find the center on the X axis for the top and bottom left pixels 
				//leftCenter = (float) (Math.floor(srcX - 0.5) + 0.5);
				//find the distance between the srcX and the leftCenter X
				float xDist = srcX - closestX;
				
				//find the center on the Y axis for the top pixels
				//topCenter = (float) (Math.floor(srcY - 0.5) + 0.5);
				//find the distance between the srcY and the topCenter Y
				float yDist = srcY - closestY;
				
				/* calculate the ratios of how much the left and right pixel will be included in 
				 * the final grey value.
				 * the left-right (x - axis) ratio for the top and bottom pair of pixels are the
				 * same so we re-use the ratio.
				 */
				rightRatio = xDist;
				leftRatio = 1 - xDist;
				

				/* calculate the ratios of how much the combined top and bottom pixel pairs will
				 * be included in the final value.
				 */
				bottomRatio = yDist;
				topRatio = 1 - yDist;

				
				//color values for the 4 corners of expanded square from gamma-adjusted dataset
				topLeftColor = gammaLookup[slice][closestY][closestX]; //y + 1
				topRightColor = gammaLookup[slice][closestY][closestX + 1]; //y + 1
				bottomLeftColor = gammaLookup[slice][closestY + 1][closestX];
				bottomRightColor = gammaLookup[slice][closestY + 1][closestX + 1];
		
				//colour interpolated between the top pair of pixels
				xTopInterpolation = (float) (topLeftColor * leftRatio + topRightColor * rightRatio);
				//colour interpolated between bottom pair of pixels
				xBottomInterpolation = (float) (bottomLeftColor * leftRatio + bottomRightColor * rightRatio);
				//colour interpolated between top and bottom pair interpolation
				yFinalInterpolation = (float) (xTopInterpolation * topRatio + xBottomInterpolation * bottomRatio);
				
				Color color = Color.color(yFinalInterpolation, yFinalInterpolation, yFinalInterpolation);
				image_writer.setColor(x, y, color);
			}
		}
		return scaledImage;
	}
	
	
	//Scales an image to the desired image size using a nearest neighbor algorithm
	public Image nearestNeighbor(int slice, float pixelScale) {
		int srcWidth = 256;
		int srcHeight = 256;
		int destWidth = (int) pixelScale;
		int destHeight = (int) pixelScale;
		
		WritableImage scaledImage = new WritableImage(destWidth, destHeight);
		
		PixelWriter image_writer = scaledImage.getPixelWriter();
		
		double val;
		
		//for every pixel in the new image
		for (int y = 0; y < destHeight; y++) {
			for (int x = 0; x < destWidth; x++) {
				float currentY = y;
				float currentX = x;
				
				//calculate the greyscale value in the scaled image based on a ratio of the old image
				int srcY = (int) Math.min(Math.floor(srcHeight * (currentY/destHeight)), srcHeight); 
				int srcX = (int) Math.min(Math.floor(srcWidth * (currentX/destWidth)), srcWidth);
				
				//get value from gamma adjusted data set
				val = gammaLookup[slice][srcY][srcX];
				Color color = Color.color(val,val,val);
				
				image_writer.setColor(x, y, color);
			}
		}	
		return scaledImage;
	}
	
	public Image generateThumbnails() {
		double numSlices = grey.length;
		int currentSlice = 0; //counter
		int columns = (int) Math.ceil(numSlices/rows);
		int thumbImageHeight = rows * (thumbnailSize + slicePadding);
		int thumbImageWidth = columns * (thumbnailSize + slicePadding);
		
		System.out.println("image width: " + thumbImageWidth + " image height: " + thumbImageHeight
				+ " columns: " + columns + " rows: " + rows);
		
		WritableImage thumbImage = new WritableImage(thumbImageWidth, thumbImageHeight);
		PixelWriter imageWriter = thumbImage.getPixelWriter(); 
		
		//for each column and row coordinate in the thumbnail image
		for (int row = 0; row < rows; row++) {
			for (int column = 0; column < columns; column++) {
				//get the current slice to be added to the thumbnail image
				Image sliceImage = nearestNeighbor(currentSlice, thumbnailSize);
				PixelReader sliceReader = sliceImage.getPixelReader();

				if(currentSlice < numSlices - 1) {
					/* read each pixel of the slice and add to the correct location in 
					 * the thumbnail view based on the current column and row
					 */
					for (int y = 0; y < sliceImage.getHeight(); y++) {
						for (int x = 0; x < sliceImage.getWidth(); x++) {
							Color slicePixel = sliceReader.getColor(x, y);
							
							int xBasedOnColumn = (thumbnailSize + slicePadding) * (column); 
							int yBasedOnRow = (thumbnailSize + slicePadding) * (row); 
							
//							System.out.println("x Based on column: " + xBasedOnColumn + " x: " + x);
							imageWriter.setColor((xBasedOnColumn + x), (yBasedOnRow + y), slicePixel);
						}
					}
					//once the slice has been completed, increment the current slice by 1
					currentSlice++;
				}
			}
		}
		
		return thumbImage;
	}
	
	public int findSliceFromThumbnail(double d, double e) {
		double numSlices = grey.length;
		int columns = (int) Math.ceil(numSlices/rows);
		
		int xPosToColumn = (int) Math.floor(d/(thumbnailSize + slicePadding));
		int yPosToRow = (int) Math.floor(e/(thumbnailSize + slicePadding));
		int selectedSlice = (columns * yPosToRow) + xPosToColumn;
		
		return selectedSlice;
	}
	
	public void ThumbWindow(double atX, double atY) {
		StackPane ThumbLayout = new StackPane();
		
		Image thumbImage = generateThumbnails();
		ImageView thumb_view = new ImageView(thumbImage);
		ThumbLayout.getChildren().add(thumb_view);
		
		
		Scene ThumbScene = new Scene(ThumbLayout, thumbImage.getWidth(), thumbImage.getHeight());
		
		//Add mouse over handler - the large image is change to the image the mouse is over
		thumb_view.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_MOVED, event -> {
			int selectedSlice = findSliceFromThumbnail(event.getX(), event.getY());
			
			System.out.println("x: " + event.getX()+ " y: " +event.getY() 
			+ " slice: " + selectedSlice);		
		
			currentSlice = selectedSlice;
			TopView.setImage(null); //clear the old image
			Image newImage = null;
			newImage = GetSlice(selectedSlice);
			TopView.setImage(newImage);
			event.consume();
		});
	
		//Build and display the new window
		Stage newWindow = new Stage();
		newWindow.setTitle("CThead Slices");
		newWindow.setScene(ThumbScene);
	
		// Set position of second window, related to primary window.
		newWindow.setX(atX);
		newWindow.setY(atY);
	
		newWindow.show();
	}
	
    public static void main(String[] args) {
        launch();
    }

}