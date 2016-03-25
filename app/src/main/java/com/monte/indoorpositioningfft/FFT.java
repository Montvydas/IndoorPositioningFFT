package com.monte.indoorpositioningfft;

/*
*  Copyright 2006-2007 Columbia University.
*
*  This file is part of MEAPsoft.
*
*  MEAPsoft is free software; you can redistribute it and/or modify
*  it under the terms of the GNU General Public License version 2 as
*  published by the Free Software Foundation.
*
*  MEAPsoft is distributed in the hope that it will be useful, but
*  WITHOUT ANY WARRANTY; without even the implied warranty of
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
*  General Public License for more details.
*
*  You should have received a copy of the GNU General Public License
*  along with MEAPsoft; if not, write to the Free Software
*  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
*  02110-1301 USA
*
*  See the file "COPYING" for the text of the license.
*/
public class FFT {

    int n, m;

    // Lookup tables. Only need to recompute when size of FFT changes.
    double[] cos;
    double[] sin;

    double[] window;

    public FFT(int n) {
        this.n = n;
        this.m = (int) (Math.log(n) / Math.log(2));

        // Make sure n is a power of 2
        if (n != (1 << m))
            throw new RuntimeException("FFT length must be power of 2");

        // precompute tables
        cos = new double[n / 2];
        sin = new double[n / 2];

        for (int i = 0; i < n / 2; i++) {
            cos[i] = Math.cos(-2 * Math.PI * i / n);
            sin[i] = Math.sin(-2 * Math.PI * i / n);
        }
        makeWindow();

    }

    protected void makeWindow() {
        // Make a blackman window:
        // w(n)=0.42-0.5cos{(2*PI*n)/(N-1)}+0.08cos{(4*PI*n)/(N-1)};
        window = new double[n];
        for(int i = 0; i < window.length; i++)
            window[i] = 0.42 - 0.5 * Math.cos(2*Math.PI*i/(n-1)) + 0.08 * Math.cos(4*Math.PI*i/(n-1));
    }

    public double[] getWindow() {
        return window;
    }

    public void fft(double[] x, double[] y) {
        int i, j, k, n1, n2, a;
        double c, s, t1, t2;

        // Bit-reverse
        j = 0;
        n2 = n / 2;
        for (i = 1; i < n - 1; i++) {
            n1 = n2;
            while (j >= n1) {
                j = j - n1;
                n1 = n1 / 2;
            }
            j = j + n1;

            if (i < j) {
                t1 = x[i];
                x[i] = x[j];
                x[j] = t1;
                t1 = y[i];
                y[i] = y[j];
                y[j] = t1;
            }
        }

        // FFT
        n1 = 0;
        n2 = 1;

        for (i = 0; i < m; i++) {
            n1 = n2;
            n2 = n2 + n2;
            a = 0;

            for (j = 0; j < n1; j++) {
                c = cos[a];
                s = sin[a];
                a += 1 << (m - i - 1);

                for (k = j; k < n; k = k + n2) {
                    t1 = c * x[k + n1] - s * y[k + n1];
                    t2 = s * x[k + n1] + c * y[k + n1];
                    x[k + n1] = x[k] - t1;
                    y[k + n1] = y[k] - t2;
                    x[k] = x[k] + t1;
                    y[k] = y[k] + t2;
                }
            }
        }
    }


    /*
    i: Bin to access
    samples: Sampling rate in Hertz (i.e. 8000 Hz, 44100Hz, etc.)
    nFFT: Size of the FFT vector
     */

    public static double Index2Freq(int binNr, double samplingFreq, int fftSize) {
        return samplingFreq * binNr / (fftSize * 1.0);
    }

    public static double[] getMagnitude (double[] re, double[] im){
        double [] magnitude = new double[re.length];
        for (int i = 0; i < re.length; i++)
            magnitude[i] =  Math.sqrt(re[i]*re[i] + im[i]*im[i]);
        return magnitude;
    }

    public static int Freq2Index(double freq, double samples, int nFFT) {
        return (int) (freq / (samples / nFFT / 2.0));
    }
}
