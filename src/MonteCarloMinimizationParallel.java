package MonteCarloMini;

import java.util.concurrent.RecursiveTask;
import java.util.concurrent.ForkJoinPool;
import java.util.Random;
//package MonteCarloMini;

 
public class MonteCarloMinimizationParallel extends RecursiveTask<Integer> {

static final boolean DEBUG=false;
         
        final Random rand;
        final int lo;
        final SearchParallel[] searches;
        final int hi;
       static long startTime = 0;
        static long endTime = 0;
     static int min;

 
        static final int sequential_threshold=1000; // depends on machine architecture

        MonteCarloMinimizationParallel(int l, int h, SearchParallel[] searches, Random rand){
       lo= l;
       hi=h;
       this.searches=searches;
       this.rand=rand;

}//constructor end
public Integer compute(){
if (hi-lo<=sequential_threshold){
       min= Integer.MAX_VALUE;//set to maxvalue
      for (int i= lo; i<hi;i++)
          {int localmin=searches[i].find_valleys();
       if (!searches[i].isStopped() && localmin<min){
      min= localmin;
       }// end of inner if
      } //for loop
      return min;
      }//if statement
      else{ 
 MonteCarloMinimizationParallel left = new  MonteCarloMinimizationParallel(lo,(hi+lo)/2,searches,rand);

MonteCarloMinimizationParallel right = new  MonteCarloMinimizationParallel((hi+lo)/2,hi,searches,rand);     
  left.fork(); //starts parallel thread
   int rightAns=right.compute();// gives main thread something to do ontop of handing out tasks
   int  leftAns=left.join(); //waits for left thread to finish, then gets its answer    
   return Math.min(leftAns, rightAns); // returns the samller value of the two
     }//end of else 
  
     }//  return min;end of compute
  //end of class
     private static void tick(){
                startTime = System.currentTimeMillis();
        }
        private static void tock(){
                endTime=System.currentTimeMillis(); 
        }

  public static void main(String [] args) {
  
  if (args.length !=7){
  System.out.println("Incorrect number of command line arguments provided.");   	
    		System.exit(0);
    	}
    	/* Read argument values */
    	int rows =Integer.parseInt( args[0] );
    	int columns = Integer.parseInt( args[1] );
    	double xmin = Double.parseDouble(args[2] );
    	double xmax = Double.parseDouble(args[3] );
    	double ymin = Double.parseDouble(args[4] );
    	double ymax = Double.parseDouble(args[5] );
    	double searches_density = Double.parseDouble(args[6] );     
        TerrainArea terrain= new TerrainArea(rows,columns,xmin,xmax,ymin,ymax);
      ForkJoinPool pool= new ForkJoinPool();
      Random rand = new Random();
        
       
        int num_searches= (int)(rows * columns * searches_density);
        SearchParallel[] searches= new SearchParallel[num_searches];
         for (int i=0; i < num_searches; i++){
         searches[i]= new SearchParallel(i+1,rand.nextInt(rows),rand.nextInt(columns),terrain);
         } //for end
        tick();  
         MonteCarloMinimizationParallel task= new MonteCarloMinimizationParallel(0,num_searches,searches,rand);
         int globalMin = pool.invoke(task);
         
         tock(); //stop timer

          System.out.println("rows " + rows + "cols: "+ columns);
          System.out.println("Total time: " + endTime +" " + startTime +" "+ (endTime-startTime));
          System.out.println("global min " + globalMin);
          }
}//end of main