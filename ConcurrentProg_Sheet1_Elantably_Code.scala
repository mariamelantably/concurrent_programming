import ox.scl._

//Question 3
class Merge{
    def merge(left: ?[Int], right: ?[Int], out: ![Int]) = thread("Merge"){
        var l = left?()
        var r = right?()
        while(True){
            if (l < r){
                out!l
                l = left?()
            }
            else{
                out!r
                r = right?()
           }
        }
        out.endOfStream
    }
}

//Question 4

/** A network to sort n numbers, using a pipeline. */
abstract class PipeSort(n: Int){

    /** The sorting network. It generates the threads needed to run the PipeSort.
     It does not run the threads. */
    def apply() : ThreadGroup 


    /** The output port to send numbers down the pipeline. */
    val left : OutPort[Int]

    /** The input channel to read the output of the pipeline. */
    val right : InPort[Int]
}

class MyPipeSort(n: Int) extends PipeSort(n) {
    val left = new SyncChan[Int] //this is the port through which we enter the numbers
    val right = new SyncChan[Int] //this is the port through which we output the numbers
    def component(left : InPort[Int], right : OutPort[Int]) = thread{
        var max = 0 
        var rec = 0 
        var count = 0 
        repeat{
            rec = left?()
            if (count == 0) {max = rec ; count += 1}            
            else{
                if (max < rec){
                    right!max //send our previous max to the next component
                    max = rec
                }
                else{
                    right!rec //we pass this element to the next component
                }
            }
        }
        //this means out in port has closed, so we're done receiving
        //we can send down our max value then close the out port
        right!max
        right.endOfStream
    }

    //now, we need to make our channels for transfer between the components
    def apply() : ThreadGroup = {
        if (n == 1){
            var threads = component(left, right) //we only need one thread in the threadgroup for length 1
            return threads
        }
        else{
            //we need n channels, so we make them here
            var channels = new Array[SyncChan[Int]](n - 1)
            for (i <- 0 until n - 1) channels(i) = new SyncChan[Int]
            var threads = component(left, channels(0))
            for (i <- 0 until n - 2) {
                threads = threads || component(channels(i), channels(i+1))
            }
            threads = threads || component(channels(n-2), right)
            return threads
        }
    }
}

//Question 5
class MatMulBagTasks(a : Array[Array[Double]], b : Array[Array[Double]], c : Array[Array[Double]], nWorkers : Int){
    require(a.size == b.size && a(0).size == b(0).size && a(0).size == a.size) //requires the matrices to both be n x n
    private val n = a.size
    require(c.size == n && c(0).size == n) //requires c to also be n x n
    private val toWorkers = new SyncChan[(Int, Int)] 
    //channel to send to workers - we want to send the indices i,j to tell the worker to find the element at (i,j)
    private val toController = new SyncChan[(Int, Int, Double)] 
    //the worker will send back the indices i,j as well as the result at that index

    private def worker = thread("worker"){
        repeat{
            val (i, j) = toWorkers?()
            //find the dot product of the ith row of a with the jth column of b
            var x : Double = 0.0
            for (k <- 0 until n){
                x += a(i)(k)*b(k)(j)
            }
            toController!((i, j, x))
        }
    }

    private def distributor = thread("distributor"){
        //we divide into n^2 tasks
        //each task consists of finding the element at (i,j)
        for (i <- 0 until n){
            for (j <- 0 until n){
                toWorkers!(i, j)
            }
        }
        toWorkers.endOfStream
    }

    private def collector = thread("collector"){
        for (a <- 0 until n){
            for (b <- 0 until n) {
                val (i, j, k) = toController?() //read the element
                c(i)(j) = k //insert into matrix c
            }
        }
    }

    private def system = {
        val workers = || (for (i <- 0 until nWorkers) yield worker)
        workers || distributor || collector
    }

    def apply = {run(system)}
}

//Question 6
class CheckForDuplicates(a : Array[Int], b : Array[Int], nTasks: Int, nWorkers : Int){
    require(nTasks%a.size == 0) 
    //we send workers (i,j) which tells them we want them to work on array a[i..j)
    private val toWorkers = new SyncChan[(Int, Int)] 
    
    //the worker sends back the number of elements in a[i..j) such that a(k) is in b
    private val toController = new SyncChan[Int] 

    private val bMap = scala.collection.mutable.HashMap.empty[Int, Boolean]
    for (k <- b) bMap.update(k, true)
    

    private def worker = thread("worker"){
        repeat{
            val (i, j) = toWorkers?()
            //find the count of elements in a[i..j) appearing in b
            var x = 0
            for (k <- i until j){
                if (bMap.contains(a(k))) x += 1
            }
            toController!(x) //send the count
        }
    }

    private def distributor = thread("distributor"){
        //we divide into nTasks taks
        var left = 0
        val taskRange = a.size/nTasks
        for (i <- 0 until nTasks){
            val right = left + taskRange
            toWorkers!(left, right)
            left = right
        }
        toWorkers.endOfStream
    }

    private def collector = thread("collector"){
        var s = 0
        for (a <- 0 until nTasks) {
            val t = toController?() //collect the task
            s += t //add to the sum
        }
    }

    private def system = {
        val workers = || (for (i <- 0 until nWorkers) yield worker)
        workers || distributor || collector
    }

    def apply = {run(system)}
}


object ConcurrentProg_Sheet1_Elantably_Code{
    def main(args : Array[String]) = {
        for (n <- 1 to 20){ //test lengths from 1 to 20
            val xs = Array.fill(n)(scala.util.Random.nextInt(100))
            val ys = new Array[Int](n)
            val pip = new MyPipeSort(n)
            def send_nums = thread{for (x <- xs) {pip.left!x}; pip.left.endOfStream}
            def out_nums = thread{var i = 0; repeat{ys(i) = pip.right?(); i+= 1}}
            run(pip.apply() || send_nums || out_nums)
            assert(xs.sorted.sameElements(ys))
            println("Correctly sorted at length " + n.toString())
        }
    }
}