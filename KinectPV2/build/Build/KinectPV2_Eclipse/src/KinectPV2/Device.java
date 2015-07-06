package KinectPV2;

/*
 Copyright (C) 2014  Thomas Sanchez Lengeling.
 KinectPV2, Kinect for Windows v2 library for processing

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 */

import java.nio.FloatBuffer;

import com.jogamp.common.nio.Buffers;

import processing.core.PApplet;
import processing.core.PImage;
import processing.core.PVector;

/**
 * Initilice Device
 * 
 * @author Thomas Sanchez Lengeling
 *
 */
public class Device implements Constants, FaceProperties, SkeletonProperties,
		Runnable {

	static {
		int arch = Integer.parseInt(System.getProperty("sun.arch.data.model"));
		String platformName = System.getProperty("os.name");
		platformName = platformName.toLowerCase();
		System.out.println(arch + " " + platformName);
		if (arch == 64) {
			System.loadLibrary("Kinect20.Face");
			System.loadLibrary("KinectPV2");
			System.out.println("Loading KinectV2");
		} else {
			System.out.println("error loading 32bits");
		}
	}

	// IMAGES
	private Image colorImg;
	private Image depthImg;
	private Image infraredImg;
	private Image infraredLongExposureImg;
	private Image bodyTrackImg;
	private Image []  bodyTrackUsersImg;
	private Image depthMaskImg;

	
	private Image pointCloudDepthImg;

	// SKELETON
	private Skeleton[] skeletonDepth;
	private Skeleton[] skeleton3d;
	private Skeleton[] skeletonColor;

	private HDFaceData[] HDFace;

	private FaceData[] faceData;

	protected boolean runningKinect;
	protected boolean stopDevice;

	FloatBuffer pointCloudDepthPos;
	FloatBuffer pointCloudColorPos;
	FloatBuffer colorChannelBuffer;

	private PApplet parent;
	private long ptr;

	private boolean startSensor;

	/**
	 * Start device
	 * 
	 * @param _p
	 *            PApplet
	 */
	public Device(PApplet _p) {
		parent = _p;
		// SETUP IMAGES
		colorImg = new Image(parent, WIDTHColor, HEIGHTColor, PImage.ARGB);
		depthImg = new Image(parent, WIDTHDepth, HEIGHTDepth, PImage.ALPHA);
		infraredImg = new Image(parent, WIDTHDepth, HEIGHTDepth, PImage.ALPHA);

		bodyTrackImg = new Image(parent, WIDTHDepth, HEIGHTDepth, PImage.RGB);
		depthMaskImg = new Image(parent, WIDTHDepth, HEIGHTDepth, PImage.RGB);
		
	
		bodyTrackUsersImg = new Image[BODY_COUNT];
		for (int i = 0; i < BODY_COUNT; i++) {
			bodyTrackUsersImg[i] = new Image(parent, WIDTHDepth, HEIGHTDepth, PImage.RGB);
		}  
		

		infraredLongExposureImg = new Image(parent, WIDTHDepth, HEIGHTDepth,
				PImage.ALPHA);

		pointCloudDepthImg = new Image(parent, WIDTHDepth, HEIGHTDepth,
				PImage.ALPHA);

		pointCloudDepthPos = Buffers.newDirectFloatBuffer(WIDTHDepth
				* HEIGHTDepth * 3);
		pointCloudColorPos = Buffers.newDirectFloatBuffer(WIDTHColor
				* HEIGHTColor * 3);
		colorChannelBuffer = Buffers.newDirectFloatBuffer(WIDTHColor
				* HEIGHTColor * 3);

		// SETUP SKELETON
		skeletonDepth = new Skeleton[BODY_COUNT];
		for (int i = 0; i < BODY_COUNT; i++) {
			skeletonDepth[i] = new Skeleton();
		}

		skeleton3d = new Skeleton[BODY_COUNT];
		for (int i = 0; i < BODY_COUNT; i++) {
			skeleton3d[i] = new Skeleton();
		}

		skeletonColor = new Skeleton[BODY_COUNT];
		for (int i = 0; i < BODY_COUNT; i++) {
			skeletonColor[i] = new Skeleton();
		}

		// SETUP FACEDATA
		faceData = new FaceData[BODY_COUNT];
		for (int i = 0; i < BODY_COUNT; i++) {
			faceData[i] = new FaceData();
		}

		HDFace = new HDFaceData[BODY_COUNT];
		for (int i = 0; i < BODY_COUNT; i++) {
			HDFace[i] = new HDFaceData();
		}

		// FloatBuffer.allocate( WIDTHDepth * HEIGHTDepth * 3);
		startSensor = false;

		jniDevice();

	}

	protected void initDevice() {
		startSensor = jniInit();
		String load = jniVersion();
		System.out.println("Version: " + load);
		
		if (startSensor == false) {
			System.out.println("ERROR STARTING KINECT V2");
			parent.exit();
		}

		if (startSensor) {
			runningKinect = true;
			(new Thread(this)).start();
		}
	}

	// COPY IMAGES TYPES FROM JNI FUNTIONS
	// private static synchronized int [] copyColorImg(int [] rawData){
	private void copyColorImg(int[] rawData) {

		// if(copyColorReady) {
		if (rawData.length == colorImg.getImgSize()) {
			// colorImg.loadPixels();
			// if(jniColorReady()) {
			PApplet.arrayCopy(rawData, 0, colorImg.pixels(), 0,
					colorImg.getImgSize());
			colorImg.updatePixels();

			if (colorImg.isProcessRawData())
				PApplet.arrayCopy(rawData, 0, colorImg.rawIntData, 0,
						colorImg.getImgSize());

			// copyColorReady = false;
			// jniColorReadyCopy(true);
		}

		// System.out.println("got");
	}

	public PImage copyColorImg() {
		int[] rawData = jniGetColorData();
		if (rawData.length == colorImg.getImgSize()) {
			PApplet.arrayCopy(rawData, 0, colorImg.pixels(), 0,
					colorImg.getImgSize());
			colorImg.updatePixels();
			// jniColorReadyCopy(true);
		}

		return colorImg.getImage();
	}

	private void copyDepthImg(int[] rawData) {
		if (rawData.length == depthImg.getImgSize()) {
			PApplet.arrayCopy(rawData, 0, depthImg.pixels(), 0,
					depthImg.getImgSize());
			depthImg.updatePixels();

			if (depthImg.isProcessRawData())
				PApplet.arrayCopy(rawData, 0, depthImg.rawIntData, 0,
						depthImg.getImgSize());

		}

	}

	// IMAGES
	/**
	 * Get Color Image as PImage 1920 x 1080
	 * 
	 * @return PImage
	 */
	public PImage getColorImage() {
		int[] colorData = jniGetColorData();
		PApplet.arrayCopy(colorData, 0, colorImg.pixels(), 0,
				colorImg.getImgSize());
		colorImg.updatePixels();

		if (colorImg.isProcessRawData())
			PApplet.arrayCopy(colorData, 0, colorImg.rawIntData, 0,
					colorImg.getImgSize());

		return colorImg.img;
	}

	/**
	 * Get Depth Image as PImage 512 x 424
	 * 
	 * @return PImage
	 */
	public PImage getDepthImage() {
		int[] depthData = jniGetDepthData();
		PApplet.arrayCopy(depthData, 0, depthImg.pixels(), 0,
				depthImg.getImgSize());
		depthImg.updatePixels();

		if (depthImg.isProcessRawData())
			PApplet.arrayCopy(depthData, 0, depthImg.rawIntData, 0,
					depthImg.getImgSize());

		// jniDepthReadyCopy(true);
		return depthImg.img;
	}

	/**
	 * get Depth Mask Image, outline color of the users.
	 * 
	 * @return PImage
	 */
	public PImage getDepthMaskImage() {
		int[] depthMaskData = jniGetDepthMask();
		PApplet.arrayCopy(depthMaskData, 0, depthMaskImg.pixels(), 0,
				depthMaskImg.getImgSize());
		depthMaskImg.updatePixels();

		if (depthMaskImg.isProcessRawData())
			PApplet.arrayCopy(depthMaskData, 0, depthMaskImg.rawIntData, 0,
					depthMaskImg.getImgSize());

		// jniDepthReadyCopy(true);
		return depthMaskImg.img;
	}

	/**
	 * Get InfraredImage as PImage 512 x 424
	 * 
	 * @return PImage
	 */
	public PImage getInfraredImage() {
		int[] infraredData = jniGetInfraredData();
		PApplet.arrayCopy(infraredData, 0, infraredImg.pixels(), 0,
				infraredImg.getImgSize());
		infraredImg.updatePixels();

		if (infraredImg.isProcessRawData())
			PApplet.arrayCopy(infraredData, 0, infraredImg.rawIntData, 0,
					infraredImg.getImgSize());

		return infraredImg.img;
	}

	/**
	 * Get BodyTracking as PImage 512 x 424
	 * 
	 * @return PImage
	 */
	public PImage getBodyTrackImage() {
		int[] bodyTrackData = jniGetBodyTrack();
		PApplet.arrayCopy(bodyTrackData, 0, bodyTrackImg.pixels(), 0,
				bodyTrackImg.getImgSize());
		bodyTrackImg.updatePixels();

		if (bodyTrackImg.isProcessRawData())
			PApplet.arrayCopy(bodyTrackData, 0, bodyTrackImg.rawIntData, 0,
					bodyTrackImg.getImgSize());

		return bodyTrackImg.img;
	}
	
	public void generteBodyTrackUsers(){
		for(int  i  = 0; i < BODY_COUNT; i++){
			int[] rawData = jniGetBodyIndexUser(i);
			PApplet.arrayCopy(rawData, 0, bodyTrackUsersImg[i].pixels(), 0, bodyTrackUsersImg[i].getImgSize());
			bodyTrackUsersImg[i].updatePixels();
		}
	}

	/**
	 * Get Independe Body Index Track
	 * 
	 * @param index
	 * @return
	 */
	public PImage getBodyTrackUser(int index) {
		if(index >= 0 && index <= 5)
			return bodyTrackUsersImg[index].img;
		return  bodyTrackImg.img;
	}

	/**
	 * Set number of Users to detect for bodyTrack(bodyIndex) or depthMask.
	 * 
	 * @param number
	 *            int val 1 - 6
	 */
	public void setNumberOfUsers(int val) {
		jniSetNumberOfUsers(val);
	}

	/**
	 * Get Long Exposure Infrared Image as PImage 512 x 424
	 * 
	 * @return PImage
	 */
	public PImage getInfraredLongExposureImage() {
		int[] longExposureData = jniGetInfraredLongExposure();
		PApplet.arrayCopy(longExposureData, 0,
				infraredLongExposureImg.pixels(), 0,
				infraredLongExposureImg.getImgSize());
		infraredLongExposureImg.updatePixels();

		if (infraredLongExposureImg.isProcessRawData())
			PApplet.arrayCopy(infraredLongExposureImg, 0,
					infraredLongExposureImg.rawIntData, 0,
					infraredLongExposureImg.getImgSize());

		return infraredLongExposureImg.img;
	}

	/**
	 * Get Skeleton as Joints with Positions and Tracking states in 3D, (x,y,z)
	 * joint and orientation, Skeleton up to 6 users
	 * 
	 * @return Skeleton []
	 */
	public Skeleton[] getSkeleton3d() {
		float[] rawData = jniGetSkeleton3D();
		for (int i = 0; i < BODY_COUNT; i++) {
			skeleton3d[i].createSkeletonData(rawData, i);
		}
		return skeleton3d;
	}

	/**
	 * Get Skeleton as Joints with Positions and Tracking states base on Depth
	 * Image, Skeleton with only (x, y) skeleton position mapped to the depth
	 * Image, get z value from the Depth Image.
	 * 
	 * @return Skeleton []
	 */
	public Skeleton[] getSkeletonDepthMap() {
		float[] rawData = jniGetSkeletonDepth();
		for (int i = 0; i < BODY_COUNT; i++) {
			skeletonDepth[i].createSkeletonData(rawData, i);
		}
		return skeletonDepth;
	}

	/**
	 * Get Skeleton as Joints with Positions and Tracking states base on color
	 * Image,
	 * 
	 * @return Skeleton []
	 */
	public Skeleton[] getSkeletonColorMap() {
		float[] rawData = jniGetSkeletonColor();
		for (int i = 0; i < BODY_COUNT; i++) {
			skeletonColor[i].createSkeletonData(rawData, i);
		}
		return skeletonColor;
	}

	// FACE DATA

	/**
	 * Generate Face Data for color map and infrared map
	 */
	public void generateFaceData() {
		float[] rawFaceColorData = jniGetFaceColorData();
		float[] rawFaceInfraredData = jniGetFaceInfraredData();

		for (int i = 0; i < BODY_COUNT; i++)
			faceData[i].createFaceData(rawFaceColorData, rawFaceInfraredData, i);

	}

	public HDFaceData[] getHDFaceVertex() {
		float[] rawData = jniGetHDFaceDetection();
		for (int i = 0; i < BODY_COUNT; i++)
			HDFace[i].createHDFaceVertexData(rawData, i);
		return HDFace;
	}

	public FaceData[] getFaceData() {
		return faceData;
	}

	// POINT CLOUDS

	/**
	 * Get Point Cloud Depth Map as FloatBuffer
	 * 
	 * @return FloatBuffer
	 */
	public FloatBuffer getPointCloudDepthPos() {
		float[] pcRawData = jniGetPointCloudDeptMap();
		pointCloudDepthPos.put(pcRawData, 0, WIDTHDepth * HEIGHTDepth * 3);
		pointCloudDepthPos.rewind();

		return pointCloudDepthPos;
	}

	public FloatBuffer getPointCloudColorPos() {
		float[] pcRawData = jniGetPointCloudColorMap();
		pointCloudColorPos.put(pcRawData, 0, WIDTHColor * HEIGHTColor * 3);
		pointCloudColorPos.rewind();

		return pointCloudColorPos;
	}

	/**
	 * Get the color channel buffer
	 * 
	 * @return
	 */
	public FloatBuffer getColorChannelBuffer() {
		float[] pcRawData = jniGetColorChannel();
		colorChannelBuffer.put(pcRawData, 0, WIDTHColor * HEIGHTColor * 3);
		colorChannelBuffer.rewind();

		return colorChannelBuffer;
	}

	public void enablePointCloud(boolean toggle) {
		jniEnablePointCloud(toggle);
	}

	// positions
	private void copyPointCloudColor(float[] rawData) {

	}

	public PImage getPointCloudDepthImage() {
		int[] rawData = jniGetPointCloudDepthImage();
		PApplet.arrayCopy(rawData, 0, pointCloudDepthImg.pixels(), 0,
				pointCloudDepthImg.getImgSize());
		pointCloudDepthImg.updatePixels();

		if (pointCloudDepthImg.isProcessRawData())
			PApplet.arrayCopy(pointCloudDepthImg, 0,
					pointCloudDepthImg.rawIntData, 0,
					pointCloudDepthImg.getImgSize());

		return pointCloudDepthImg.img;
	}

	/**
	 * Set Threshold Depth Value Z for Point Cloud
	 * 
	 * @param float val
	 */
	public void setLowThresholdPC(int val) {
		jniSetLowThresholdDepthPC(val);
	}

	/**
	 * Get Threshold Depth Value Z from Point Cloud Default 1.9
	 * 
	 * @return default Threshold
	 */
	public int getLowThresholdDepthPC() {
		return jniGetLowThresholdDepthPC();
	}

	/**
	 * Set Threshold Depth Value Z for Point Cloud
	 * 
	 * @param float val
	 */
	public void setHighThresholdPC(int val) {
		jniSetHighThresholdDepthPC(val);
	}

	/**
	 * Get Threshold Depth Value Z from Point Cloud Default 1.9
	 * 
	 * @return default Threshold
	 */
	public int getHighThresholdDepthPC() {
		return jniGetHighThresholdDepthPC();
	}

	/**
	 * Get Raw Depth Data 512 x 424
	 * 
	 * @return int []
	 */
	public int[] getRawDepth() {
		return depthImg.rawIntData;
	}

	/**
	 * Get Raw DepthMask Data 512 x 424
	 * 
	 * @return int []
	 */
	public int[] getRawDepthMask() {
		return depthMaskImg.rawIntData;
	}

	/**
	 * Get Raw Color Data 1920 x 1080
	 * 
	 * @return int []
	 */
	public int[] getRawColor() {
		return colorImg.rawIntData;
	}

	/**
	 * Get Raw Infrared Data 512 x 424
	 * 
	 * @return int []
	 */
	public int[] getRawInfrared() {
		return infraredImg.rawIntData;
	}

	/**
	 * Get Raw BodyTracking Data 512 x 424
	 * 
	 * @return int []
	 */
	public int[] getRawBodyTrack() {
		return bodyTrackImg.rawIntData;
	}

	/**
	 * Get Raw LongExposure Data 512 x 424
	 * 
	 * @return int []
	 */
	public int[] getRawLongExposure() {
		return infraredLongExposureImg.rawIntData;
	}

	// ACTIVATE RAW DATA
	/**
	 * Activate Raw Color Image Capture. Use getRawColor() Method
	 * 
	 * @param boolean toggle
	 */
	public void activateRawColor(boolean toggle) {
		colorImg.activateRawData(toggle);
	}

	/**
	 * Activate Raw Depth Image Capture Use getRawDepth() Method
	 * 
	 * @param boolean toggle
	 */
	public void activateRawDepth(boolean toggle) {
		depthImg.activateRawData(toggle);
	}

	/**
	 * Activate Raw Depth Image Capture Use getDepthMaskRaw() Method
	 * 
	 * @param boolean toggle
	 */
	public void activateRawDepthMaskImg(boolean toggle) {
		depthMaskImg.activateRawData(toggle);
	}

	/**
	 * Activate Raw Infrared Image Capture Use getRawInfrared() Method
	 * 
	 * @param boolean toggle
	 */
	public void activateRawInfrared(boolean toggle) {
		infraredImg.activateRawData(toggle);
	}

	/**
	 * Activate Raw BodyTrack Image Capture Use getRawBodyTrack() Method
	 * 
	 * @param boolean toggle
	 */
	public void activateRawBodyTrack(boolean toggle) {
		bodyTrackImg.activateRawData(toggle);
	}

	/**
	 * Activate Raw LongExposureInfrared Image Capture use getRawLongExposure()
	 * method
	 * 
	 * @param boolean toggle
	 */
	public void activateRawLongExposure(boolean toggle) {
		infraredLongExposureImg.activateRawData(toggle);
	}

	/**
	 * Enable or Disable Color Image Capture
	 * 
	 * @param boolean toggle
	 */
	public void enableColorImg(boolean toggle) {
		jniEnableColorFrame(toggle);
	}

	/**
	 * Enable or disable enableColorChannel 3 independent color channels 1920 x
	 * 1080 x 3 from 0-1 Ideally for openGL calls
	 * 
	 * @param toggle
	 */
	public void enableColorChannel(boolean toggle) {
		jniEnableColorChannel(toggle);
	}

	/**
	 * Enable or Disable Depth Image Capture
	 * 
	 * @param boolean toggle
	 */
	public void enableDepthImg(boolean toggle) {
		jniEnableDepthFrame(toggle);
	}

	/**
	 * Enable or Disable DepthMask Image Capture
	 * 
	 * @param boolean toggle
	 */
	public void enableDepthMaskImg(boolean toggle) {
		jniEnableDepthMaskFrame(toggle);
	}

	/**
	 * Enable or Disable Infrared Image Capture
	 * 
	 * @param boolean toggle
	 */
	public void enableInfraredImg(boolean toggle) {
		jniEnableInfraredFrame(toggle);
	}

	/**
	 * Enable or Disable BodyTrack Image Capture
	 * 
	 * @param boolean toggle
	 */
	public void enableBodyTrackImg(boolean toggle) {
		jniEnableBodyTrackFrame(toggle);
	}

	/**
	 * Enable or Disable LongExposure Infrared Image Capture
	 * 
	 * @param boolean toggle
	 */
	public void enableInfraredLongExposureImg(boolean toggle) {
		jniEnableInfraredLongExposure(toggle);
	}

	/**
	 * Enable or Disable Skeleton Depth Map Capture
	 * 
	 * @param boolean toggle
	 */
	public void enableSkeletonDepthMap(boolean toggle) {
		jniEnableSkeletonDepth(toggle);
	}

	/**
	 * Enable or Disable Skeleton Color Map Capture
	 * 
	 * @param boolean toggle
	 */
	public void enableSkeletonColorMap(boolean toggle) {
		jniEnableSkeletonColor(toggle);
	}

	/**
	 * Enable or Disable Skeleton 3D Map Capture
	 * 
	 * @param boolean toggle
	 */
	public void enableSkeleton3DMap(boolean toggle) {
		jniEnableSkeleton3D(toggle);
	}

	/**
	 * Enable or Disable Face Tracking
	 * 
	 * @param boolean toggle
	 */
	public void enableFaceDetection(boolean toggle) {
		jniEnableFaceDetection(toggle);
	}

	/**
	 * Enable HDFace detection
	 * 
	 * @param toggle
	 */
	public void enableHDFaceDetection(boolean toggle) {
		jniEnableHDFaceDetection(toggle);
	}

	/*
	 * public void enableMirror(boolean toggle){ jniSetMirror(toggle); }
	 */
	
	//MAPPERS
	public PVector MapCameraPointToDepthSpace(PVector pos){
		float [] rawData = jniMapCameraPointToDepthSpace(pos.x, pos.y, pos.z);
		return new PVector(rawData[0], rawData[1]);
	}
	
	public PVector MapCameraPointToColorSpace(PVector pos){
		float [] rawData = jniMapCameraPointToColorSpace(pos.x, pos.y, pos.z);
		return new PVector(rawData[0], rawData[1]);
	}
		
		
	protected boolean updateDevice() {
		boolean result = jniUpdate();
		return result;
	}

	protected void stopDevice() {
		jniStopDevice();
	}
	
	protected void cleanDevice() {
		boolean val  = jniStopSignal();
	}

	// ------JNI FUNCTIONS
	private native void jniDevice();

	private native boolean jniInit();

	private native String jniVersion();

	private native boolean jniUpdate();

	// STOP
	private native void jniStopDevice();

	private native boolean jniStopSignal();

	// ENABLE FRAMES
	private native void jniEnableColorFrame(boolean toggle);

	private native void jniEnableColorChannelsFrame(boolean toggle);

	private native void jniEnableDepthFrame(boolean toggle);

	private native void jniEnableDepthMaskFrame(boolean toggle);

	private native void jniEnableInfraredFrame(boolean toggle);

	private native void jniEnableBodyTrackFrame(boolean toggle);

	private native void jniEnableInfraredLongExposure(boolean toggle);

	private native void jniEnableSkeletonDepth(boolean toggle);

	private native void jniEnableSkeletonColor(boolean toggle);

	private native void jniEnableSkeleton3D(boolean toggle);

	private native void jniEnableFaceDetection(boolean toggle);

	private native void jniEnableHDFaceDetection(boolean toggle);

	private native void jniEnablePointCloud(boolean toggle);

	// COLOR CHANNEL
	private native void jniEnableColorChannel(boolean toggle);

	private native float[] jniGetColorChannel();

	// DEPTH
	private native int[] jniGetColorData();

	private native int[] jniGetDepthData();

	private native int[] jniGetInfraredData();

	private native int[] jniGetInfraredLongExposure();

	private native int[] jniGetBodyTrack();

	private native int[] jniGetDepthMask();

	private native float[] jniGetSkeleton3D();

	private native float[] jniGetSkeletonDepth();

	private native float[] jniGetSkeletonColor();

	private native float[] jniGetFaceColorData();

	private native float[] jniGetFaceInfraredData();

	private native float[] jniGetHDFaceDetection();

	// POINT CLOUD
	private native float[] jniGetPointCloudDeptMap();

	private native float[] jniGetPointCloudColorMap();

	private native int[] jniGetPointCloudDepthImage();

	// PC THRESHOLDS
	private native void jniSetLowThresholdDepthPC(int val);

	private native int jniGetLowThresholdDepthPC();

	private native void jniSetHighThresholdDepthPC(int val);

	private native int jniGetHighThresholdDepthPC();

	// BODY INDEX
	private native void jniSetNumberOfUsers(int index);

	private native int[] jniGetBodyIndexUser(int index);
	
	//crists
	
	//MAPERS
	private native float [] jniMapCameraPointToDepthSpace(float camaraSpacePointX, float cameraSpacePointY, float cameraSpacePointZ);
	
	private native float [] jniMapCameraPointToColorSpace(float camaraSpacePointX, float cameraSpacePointY, float cameraSpacePointZ);

	
	public void run() {
		int fr = PApplet.round(1000.0f / parent.frameRate);
		while (runningKinect) {
			// boolean result = updateDevice();

			// if(!result){
			// System.out.println("Error updating Kinect EXIT");
			// }
			try {
				Thread.sleep(fr); // 2
			} catch (InterruptedException e) {
				e.printStackTrace();
				return;
			}
		}

	}
}
