import ox.scl._

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
