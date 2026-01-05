/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2026 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package ch.epfl.biop;

/**
 * Simple tests of potential arithmetic simplification that I think
 * a JIT compiler should be able to do:
 * - Multiplications by ones
 * - Additions with zeros...
 * <p>
 * My wish is that special cases matrices computations could be auto-simplified.
 * (a translation matrix is only 3 additions in 3D)
 * Apparently we're not there yet, or simply I don't know how to trigger the magic
 * <p>
 * I tried OpenJDK 1.8, OpenJDK 18, Graalvm-ce-17, none of them were able to
 * discard what appears to be no-ops
 * <p>
 * Due to the simplicity of the code, please re-use and modify as you wish
 * @author Nicolas Chiaruttini, 25th July 2022
 */
public class JITTester {
    final Multiplier mt;

    public JITTester(double factor) {
        mt = new Multiplier(factor);
    }

    public double multiply(double d) {
        return mt.multiply(d);
    }

    final static int nRepetitions = 100_000_000;
    public static void main(String... args) {
        final double factor1 = 1.0d;
        final double factor1p1 = 1.1d;
        double value;

        System.out.println("----------------------");
        System.out.println("Repeating "+nRepetitions+" multiplication by factor1 = 1.0, a final variable");
        System.out.println("The code is put in the main method");
        System.out.println("This is overall is No op, and should be super fast");
        tic();
        value = 0.0d;
        for (int i=0;i<nRepetitions;i++) {
            for (int j=0;j<20;j++) {
                value = value * factor1;
            }
        }
        System.out.println("Result = "+value);
        toc();

        System.out.println("----------------------");
        System.out.println("Repeating "+nRepetitions+" multiplication by factor1 = 1.0, a final variable");
        System.out.println("The code is put in the Multiplier method");
        System.out.println("This is overall is No op, and should be super fast");
        tic();
        value = 0.0d;
        JITTester jt = new JITTester(factor1);
        System.out.println("Result = "+  jt.multiply(value));
        toc();

        double nonFinalFactor1 = 1.0;
        System.out.println("----------------------");
        System.out.println("Repeating "+nRepetitions+" multiplication by nonFinalFactor =  1.0, a non final variable");
        System.out.println("The code is put in the main method");
        System.out.println("This is overall is No op, and should be super fast");
        tic();
        value = 0.0d;
        for (int i=0;i<nRepetitions;i++) {
            for (int j=0;j<20;j++) {
                value = value * nonFinalFactor1;
            }
        }
        System.out.println("Result = "+value);
        toc();

        System.out.println("----------------------");
        System.out.println("Repeating "+nRepetitions+" multiplication by 1.1");
        System.out.println("The code is put in the main method");
        System.out.println("This is NOT a no op, and thus can only be slow");
        tic();
        value = 0.0d;
        for (int i=0;i<nRepetitions;i++) {
            for (int j=0;j<20;j++) {
                value = value * factor1p1;
            }
        }
        System.out.println("Result = "+value);
        toc();

        System.out.println("----------------------");
        System.out.println("Repeating "+nRepetitions+" multiplication by factor1 = 1.0d");
        System.out.println("Function called = repeatMultiply");
        System.out.println("This is overall is No op, and should be super fast");
        tic();
        value = 0.0d;
        repeatMultiply(nRepetitions, value, factor1);
        toc();

        System.out.println("----------- ADDITIONS");

        final double number0 = 0.0;
        System.out.println("----------------------");
        System.out.println("Repeating "+nRepetitions+" additions by number0 = 0.0, a final variable");
        System.out.println("The code is put in the main method");
        System.out.println("This is overall is No op, and should be super fast");
        tic();
        value = 0.0d;
        for (int i=0;i<nRepetitions;i++) {
            for (int j=0;j<20;j++) {
                value = value + number0;
            }
        }
        System.out.println("Result = "+value);
        toc();

        System.out.println("----------------------");
        System.out.println("Repeating "+nRepetitions+" additions by 0.0, explicitely written in the code");
        System.out.println("The code is put in the main method");
        System.out.println("This is overall is No op, and should be super fast");
        tic();
        value = 0.0d;
        for (int i=0;i<nRepetitions;i++) {
            for (int j=0;j<20;j++) {
                value = value + 0d;
            }
        }
        System.out.println("Result = "+value);
        toc();

    }

    public static void repeatMultiply(int nRepetitions, double value, final double multFactor) {
        for (int i=0;i<nRepetitions;i++) {
            for (int j=0;j<20;j++) {
                value = value * multFactor;
            }
        }
        System.out.println("Result = "+value);
    }

    static long startTime;

    public static void tic() {
        startTime = System.nanoTime();
    }

    public static void toc() {
        long stopTime = System.nanoTime();
        System.out.println("Elapsed time \t"+((stopTime-startTime)/1e6)+"\t ms");
    }

    public class Multiplier {
        final double factor;

        public Multiplier(double factor) {
            this.factor = factor;
        }

        public double multiply(double value) {
            //if (factor==1.0d) return value;
            for (int i=0;i<nRepetitions;i++) {
                for (int j=0;j<20;j++) {
                    value = value * factor;
                }
            }
            return value;
        }
    }

}

/*
 * Results on my machines with graalvm-ce-17\bin\java.exe, only one situation leads to optimisation
 * ----------------------
 * Repeating 100000000 multiplication by factor1 = 1.0, a final variable
 * The code is put in the main method
 * This is overall is No op, and should be super fast
 * Result = 0.0
 * Elapsed time 	64.8528	 ms
 * ----------------------
 * Repeating 100000000 multiplication by nonFinalFactor =  1.0, a non final variable
 * The code is put in the main method
 * This is overall is No op, and should be super fast
 * Result = 0.0
 * Elapsed time 	1904.0471	 ms
 * ----------------------
 * Repeating 100000000 multiplication by 1.1
 * The code is put in the main method
 * This is NOT a no op, and thus can only be slow
 * Result = 0.0
 * Elapsed time 	1855.0064	 ms
 * ----------------------
 * Repeating 100000000 multiplication by factor1 = 1.0d
 * Function called = repeatMultiply
 * This is overall is No op, and should be super fast
 * Result = 0.0
 * Elapsed time 	1815.1354	 ms
 * ----------- ADDITIONS
 * ----------------------
 * Repeating 100000000 additions by number0 = 0.0, a final variable
 * The code is put in the main method
 * This is overall is No op, and should be super fast
 * Result = 0.0
 * Elapsed time 	1812.0575	 ms
 * ----------------------
 * Repeating 100000000 additions by 0.0, explicitely written in the code
 * The code is put in the main method
 * This is overall is No op, and should be super fast
 * Result = 0.0
 * Elapsed time 	1821.8383	 ms
 */
