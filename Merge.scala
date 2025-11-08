//Question 3
class Merge{ //Merges two sorted channels that never close
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
