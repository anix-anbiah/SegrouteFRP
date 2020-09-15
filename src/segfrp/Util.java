/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package segfrp;

/**
 *
 * @author anix
 */
public class Util {
    
    public static double percentage(double num, double den) {
        
        double percentage = (num * 100.0) /den;
        
        percentage = ((int) (percentage * 100.0)) / 100.0;
        
        return percentage;
    }
    
    public static double ratio(double num, double den) {
        
        double ratio = num / den;
        ratio = ((int) (ratio * 100.0)) / 100.0;
        
        return ratio;
    }
    
}
