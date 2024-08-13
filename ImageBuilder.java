import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.canvas.*;
import javafx.scene.paint.Color;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.animation.AnimationTimer;
import javafx.geometry.Point2D;

import java.util.*;

/**
 * ImageBuilder, an image puzzle game
 * Usage: ImageBuilder image-url
 * 
 * Keys to use: SPACE (scramble), S (solve), C (clear moves), R (reset)
 * Use the mouse to manually solve the puzzle
 * 
 * @author OrangoMango
 * @version 1.0
 */
public class ImageBuilder extends Application{
	private static final int SCALE = 1;
	private static final int DELAY_TIME = 1;
	private static final int ANIMATION_TIME = 10;
	private static final int PIECE_SIZE = 50;
	private static final int MAX_PIECES_SIZE = 200;

	private static class ImagePiece{
		private int x, y;
		private int currentX, currentY;
		private double width, height; // Global width and height
		private Image image;
		private double offsetX, offsetY;
		
		public ImagePiece(Image image, int x, int y, double w, double h){
			this.image = image;
			this.x = x;
			this.y = y;
			this.width = w;
			this.height = h;
			this.currentX = this.x;
			this.currentY = this.y;
		}
		
		public void setOffsetX(double o){
			this.offsetX = o;
		}
		
		public void setOffsetY(double o){
			this.offsetY = o;
		}
		
		public void setPos(int x, int y){
			this.currentX = x;
			this.currentY = y;
		}
		
		public void render(GraphicsContext gc){
			gc.drawImage(this.image, this.x*this.width, this.y*this.height, this.width, this.height, (this.currentX+this.offsetX)*this.width*SCALE, (this.currentY+this.offsetY)*this.height*SCALE, this.width*SCALE, this.height*SCALE);
		}
	}
	
	private static class Move{
		private int direction, emptyPos, start, length;
		
		public Move(int dir, int emptyPos, int start, int length){
			this.direction = dir;
			this.emptyPos = emptyPos;
			this.start = start;
			this.length = length;
		}
		
		public Move reverse(){
			int newDir = (this.direction + 2) % 4;
			int newStart = 0;
			if (this.direction == 1 || this.direction == 2){
				newStart = this.start-1;
			} else if (this.direction == 0 || this.direction == 3){
				newStart = this.start+1;
			}

			return new Move(newDir, this.emptyPos, newStart, this.length);
		}
		
		public int getDirection(){
			return this.direction;
		}
		
		public int getEmptyPos(){
			return this.emptyPos;
		}
		
		public int getStart(){
			return this.start;
		}
		
		public int getLength(){
			return this.length;
		}
	}
	
	private static class MoveAnimation{
		private ArrayList<ImagePiece> pieces;
		private boolean hor;
		private int timeDelay;
		private double increment;
		private Runnable onFinished;
		private double offset = 0;
		
		public MoveAnimation(boolean h, ArrayList<ImagePiece> pieces, int timeDelay, double increment, Runnable onFinished){
			this.hor = h;
			this.pieces = pieces;
			this.timeDelay = timeDelay;
			this.increment = increment;
			this.onFinished = onFinished;
		}
		
		public void start() throws InterruptedException{
			while (Math.abs(offset) < 1){
				this.offset += this.increment;
					
				for (ImagePiece piece : this.pieces){
					if (this.hor){
						piece.setOffsetX(this.offset);
					} else {
						piece.setOffsetY(this.offset);
					}
				}
				
				Thread.sleep(this.timeDelay);
			}
			
			this.onFinished.run();
			for (ImagePiece piece : this.pieces){
				if (this.hor){
					piece.setOffsetX(0);
				} else {
					piece.setOffsetY(0);
				}
			}
		}
	}

	private Image currentImage;
	private double width, height;
	private ImagePiece[][] map;
	private int emptyX, emptyY;
	private Point2D clickPoint;
	private volatile boolean scramble = true;
	private ArrayList<Move> moves = new ArrayList<>();

	@Override
	public void start(Stage stage){
		List<String> params = getParameters().getRaw();
		if (params.size() == 0){
			throw new IllegalArgumentException("Usage: ImageBuilder <image-url>");
		}

		final String imageUrl = params.get(0);
		this.currentImage = new Image(imageUrl);
		this.width = this.currentImage.getWidth();
		this.height = this.currentImage.getHeight();

		if (this.width == 0 || this.height == 0){
			throw new IllegalArgumentException("Could not load image from: "+imageUrl);
		}
		
		final int w = (int)Math.ceil(this.width / PIECE_SIZE);
		final int h = (int)Math.ceil(this.height / PIECE_SIZE);
		this.map = new ImagePiece[w][h];
		this.emptyX = w-1;
		this.emptyY = h-1;
		
		System.out.println(w+"x"+h);
		
		for (int i = 0; i < w; i++){
			for (int j = 0; j < h; j++){
				if (i != this.emptyX || j != this.emptyY){
					this.map[i][j] = new ImagePiece(this.currentImage, i, j, this.width/w, this.height/h);
				}
			}
		}
		
		StackPane pane = new StackPane();
		Canvas canvas = new Canvas(this.width*SCALE, this.height*SCALE);
		GraphicsContext gc = canvas.getGraphicsContext2D();
		pane.getChildren().add(canvas);
		
		canvas.setOnMousePressed(e -> this.clickPoint = new Point2D(e.getX(), e.getY()));
		canvas.setOnMouseReleased(e -> {
			Point2D pos = new Point2D(e.getX(), e.getY());
			pos = pos.subtract(this.clickPoint);
			double angle = Math.toDegrees(Math.atan2(pos.getY(), pos.getX()));
			int location = getLocation(angle);
			
			new Thread(() -> {
				try {
					int px = (int)(this.clickPoint.getX()/SCALE/(this.width/w));
					int py = (int)(this.clickPoint.getY()/SCALE/(this.height/h));
					Move move = null;
					
					switch (location){
						case 0: // Move right
							move = new Move(location, py, px, this.emptyX-px);
							break;
						case 1: // Move up
							move = new Move(location, px, this.emptyY+1, py-this.emptyY);
							break;
						case 2: // Move left
							move = new Move(location, py, this.emptyX+1, px-this.emptyX);
							break;
						case 3: // Move down
							move = new Move(location, px, py, this.emptyY-py);
							break;
					}

					boolean valid = applyMove(move, ANIMATION_TIME);
					if (valid) this.moves.add(move);
				} catch (InterruptedException ex){
					ex.printStackTrace();
				}
			}).start();
		});
		
		canvas.setFocusTraversable(true);
		canvas.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.SPACE){
				this.scramble = !this.scramble;
			} else if (e.getCode() == KeyCode.S){
				this.scramble = false;
				reverseMoves();
			} else if (e.getCode() == KeyCode.C){
				this.moves.clear();
				System.out.println("Moves cleared");
			} else if (e.getCode() == KeyCode.R){
				reset();
				System.out.println("Reset");
			}
		});
		
		AnimationTimer loop = new AnimationTimer(){
			@Override
			public void handle(long time){
				update(gc);
			}
		};
		loop.start();
		
		Thread randomThread = new Thread(() -> {
			try {
				Thread.sleep(2500);
				while (true){
					if (!this.scramble) continue;

					Random random = new Random();
					int direction = random.nextInt(4);
					Move move = null;
					switch (direction){
						case 0: // Move right
							int px1 = getRandomNumber(this.emptyX);
							move = new Move(direction, this.emptyY, px1, this.emptyX-px1);
							break;
						case 1: // Move up
							int py1 = getRandomNumber(h-(this.emptyY+1))+this.emptyY+1;
							move = new Move(direction, this.emptyX, this.emptyY+1, py1-this.emptyY);
							break;
						case 2: // Move left
							int px2 = getRandomNumber(w-(this.emptyX+1))+this.emptyX+1;
							move = new Move(direction, this.emptyY, this.emptyX+1, px2-this.emptyX);
							break;
						case 3: // Move down
							int py2 = getRandomNumber(this.emptyY);
							move = new Move(direction, this.emptyX, py2, this.emptyY-py2);
							break;
					}
					
					boolean valid = applyMove(move, 0);
					if (valid) this.moves.add(move);
					
					if (this.moves.size() >= MAX_PIECES_SIZE){
						this.scramble = false;
					}
					
					Thread.sleep(DELAY_TIME);
				}
			} catch (InterruptedException ex){
				ex.printStackTrace();
			}
		});
		randomThread.setDaemon(true);
		randomThread.start();
		
		Scene scene = new Scene(pane, this.width*SCALE, this.height*SCALE);
		stage.setScene(scene);
		stage.setTitle(imageUrl.substring(imageUrl.lastIndexOf("/")+1));
		stage.setResizable(false);
		stage.show();
	}

	private void reset(){
		this.moves.clear();
		final int w = this.map.length;
		final int h = this.map[0].length;
		for (int i = 0; i < w; i++){
			for (int j = 0; j < h; j++){
				this.map[i][j] = new ImagePiece(this.currentImage, i, j, this.width/w, this.height/h);
			}
		}

		this.emptyX = w-1;
		this.emptyY = h-1;
		this.map[this.emptyX][this.emptyY] = null;
	}
	
	private void reverseMoves(){
		Thread thread = new Thread(() -> {
			try {
				for (int i = this.moves.size()-1; i >= 0; i--){
					Move move = this.moves.get(i).reverse();
					applyMove(move, ANIMATION_TIME);
					Thread.sleep(DELAY_TIME);
				}
		
				this.moves.clear();
			} catch (InterruptedException ex){
				ex.printStackTrace();
			}
		});
		thread.setDaemon(true);
		thread.start();
	}
	
	private boolean applyMove(Move move, int animationTime) throws InterruptedException{
		if (move.getDirection() == 0 || move.getDirection() == 2){
			return moveRow(move.getEmptyPos(), move.getStart(), move.getLength(), animationTime);
		} else if (move.getDirection() == 1 || move.getDirection() == 3){
			return moveColumn(move.getEmptyPos(), move.getStart(), move.getLength(), animationTime);
		}
		
		return false;
	}
	
	private static int getRandomNumber(int max){
		if (max == 0){
			return 0;
		} else {
			Random random = new Random();
			return random.nextInt(max);
		}
	}
	
	private static int getLocation(double angle){
		if ((angle >= 0 && angle < 45) || (angle < 0 && angle > -45)){
			return 0;
		} else if (angle <= -45 && angle > -135){
			return 1;
		} else if (angle <= -135 || angle >= 135){
			return 2;
		} else {
			return 3;
		}
	}
	
	private boolean moveColumn(int col, int rowStart, int length, int animationTime) throws InterruptedException{
		if (rowStart < 0 || rowStart+length > this.map[0].length) return false;
	
		if (col == this.emptyX){
			if (rowStart == this.emptyY+1){ // Move up
				ArrayList<ImagePiece> pieces = new ArrayList<>();
				for (int i = rowStart; i < rowStart+length; i++){
					pieces.add(this.map[this.emptyX][i]);
				}
				
				MoveAnimation anim = new MoveAnimation(false, pieces, animationTime, -0.1, () -> {
					for (int i = rowStart; i < rowStart+length; i++){
						this.map[this.emptyX][i-1] = this.map[this.emptyX][i];
						this.map[this.emptyX][i-1].setPos(this.emptyX, i-1);
					}
					this.emptyY = rowStart+length-1;
				});
				anim.start();
			} else if (rowStart+length-1 == this.emptyY-1){ // Move down
				ArrayList<ImagePiece> pieces = new ArrayList<>();
				for (int i = rowStart+length-1; i >= rowStart; i--){
					pieces.add(this.map[this.emptyX][i]);
				}
				
				MoveAnimation anim = new MoveAnimation(false, pieces, animationTime, 0.1, () -> {
					for (int i = rowStart+length-1; i >= rowStart; i--){
						this.map[this.emptyX][i+1] = this.map[this.emptyX][i];
						this.map[this.emptyX][i+1].setPos(this.emptyX, i+1);
					}
					this.emptyY = rowStart;
				});
				anim.start();
			}
		}
		
		this.map[this.emptyX][this.emptyY] = null;
		return true;
	}
	
	private boolean moveRow(int row, int colStart, int length, int animationTime) throws InterruptedException{
		if (colStart < 0 || colStart+length > this.map.length) return false;

		if (row == this.emptyY){
			if (colStart == this.emptyX+1){ // Move left
				ArrayList<ImagePiece> pieces = new ArrayList<>();
				for (int i = colStart; i < colStart+length; i++){
					pieces.add(this.map[i][this.emptyY]);
				}

				MoveAnimation anim = new MoveAnimation(true, pieces, animationTime, -0.1, () -> {
					for (int i = colStart; i < colStart+length; i++){
						this.map[i-1][this.emptyY] = this.map[i][this.emptyY];
						this.map[i-1][this.emptyY].setPos(i-1, this.emptyY);
					}
					this.emptyX = colStart+length-1;
				});
				anim.start();
			} else if (colStart+length-1 == this.emptyX-1){ // Move right
				ArrayList<ImagePiece> pieces = new ArrayList<>();
				for (int i = colStart+length-1; i >= colStart; i--){
					pieces.add(this.map[i][this.emptyY]);
				}

				MoveAnimation anim = new MoveAnimation(true, pieces, animationTime, 0.1, () -> {
					for (int i = colStart+length-1; i >= colStart; i--){
						this.map[i+1][this.emptyY] = this.map[i][this.emptyY];
						this.map[i+1][this.emptyY].setPos(i+1, this.emptyY);
					}
					this.emptyX = colStart;
				});
				anim.start();
			}
		}
		
		this.map[this.emptyX][this.emptyY] = null;
		return true;
	}
	
	private void update(GraphicsContext gc){
		gc.clearRect(0, 0, this.width*SCALE, this.height*SCALE);
		gc.setFill(Color.BLACK);
		gc.fillRect(0, 0, this.width*SCALE, this.height*SCALE);
		
		for (int i = 0; i < this.map.length; i++){
			for (int j = 0; j < this.map[0].length; j++){
				ImagePiece piece = this.map[i][j];
				if (piece != null){
					piece.render(gc);
				}
			}
		}
	}

	public static void main(String[] args){
		launch(args);
	}
}