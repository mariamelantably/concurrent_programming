import ox.scl._


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




object MatMul{
    def main(args: Array[String])= {
        val a = Array(Array(3.0,5.0), Array(0.0, 4.0))
        val b = Array(Array(1.0,2.0), Array(3.0,3.0))
        val c = Array(Array(0.0,0.0), Array(0.0,0.0))
        val pip = new MatMulBagTasks(a, b, c, )
        pip.apply
        for (i <- 0 until c.size){
            println("")
            for (j <- 0 until c.size){
                print(c(i)(j))
                print(" ")
            }

        }
    }
}