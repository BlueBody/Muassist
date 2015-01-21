/*
Musicians Assistant
    Copyright (C) 2012-2014  Zdeněk Janeček <jan.zdenek@gmail.com>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ycdmdj.muassist;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.NumberPicker.OnValueChangeListener;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;

import ycdmdj.muassist.common.Recorder;
import ycdmdj.muassist.common.SharedData;
import ycdmdj.muassist.prefs.SharedPref;
import ycdmdj.muassist.tuner.AnalyzerAMDF;
import ycdmdj.muassist.tuner.AnalyzerWave;
import ycdmdj.muassist.tuner.Classificator;
import ycdmdj.muassist.tuner.MedianFilter;
import ycdmdj.muassist.tuner.Tune;

@SuppressLint("NewApi")
public class TunerFragment extends Fragment implements IControlable, Informable {
    private static final int METHOD_AMDF = 0;
    private static final int METHOD_WAVE = 1;

    private Recorder recorder;
    private SharedData sd;
    private AsyncTask<Void, Double, Void> analyzer;
    private Classificator classify;
    private MedianFilter mf;

    private ImageView circle;
    private TextView tunerText;
    private Tune bar;
    private AlphaAnimation inAnim;
    private AlphaAnimation outAnim;
    private NumberPicker refFreq;
    private net.simonvt.numberpicker.NumberPicker refFreqOld;

    private int temp;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.tuner, container, false);
        RelativeLayout space = (RelativeLayout) v.findViewById(R.id.sunspace);
        space.setOnTouchListener(TouchControl.getInstance());

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            refFreqOld = (net.simonvt.numberpicker.NumberPicker) v.findViewById(R.id.refFreq);
            refFreqOld.setMinValue(390);
            refFreqOld.setMaxValue(460);
            refFreqOld.setOnValueChangedListener(new net.simonvt.numberpicker.NumberPicker.OnValueChangeListener() {
                @Override
                public void onValueChange(net.simonvt.numberpicker.NumberPicker picker, int oldVal, int newVal) {
                    classify.changeRef(newVal);
                }
            });
            refFreqOld.setValue(SharedPref.getConcertPitch(getActivity()));
        } else {
            refFreq = (NumberPicker) v.findViewById(R.id.refFreq);
            refFreq.setMinValue(390);
            refFreq.setMaxValue(460);
            refFreq.setOnValueChangedListener(new OnValueChangeListener() {
                @Override
                public void onValueChange(NumberPicker picker, int oldVal,
                                          int newVal) {
                    classify.changeRef(newVal);
                }
            });
            refFreq.setValue(SharedPref.getConcertPitch(getActivity()));
        }

        circle = (ImageView) v.findViewById(R.id.circle);
        tunerText = (TextView) v.findViewById(R.id.tuner_text);

        bar = (Tune) v.findViewById(R.id.tune);

        inAnim = TouchControl.getAnimation(TouchControl.ANIMATION_IN, 300);
        outAnim = TouchControl.getAnimation(TouchControl.ANIMATION_OUT, 300);

        return v;
    }

    @Override
    public void onPause() {
        analyzer.cancel(true);
        recorder.stopRecording();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            SharedPref.setConcertPitch(getActivity(), refFreqOld.getValue());
        } else {
            SharedPref.setConcertPitch(getActivity(), refFreq.getValue());
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();

        // set saved values
        getView().setBackgroundResource(
                ((MainActivity) getActivity()).getBgRes());

        temp = SharedPref.getTemp(getActivity());

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            refFreqOld.setValue(SharedPref.getConcertPitch(getActivity()));
        } else {
            refFreq.setValue(SharedPref.getConcertPitch(getActivity()));
        }

        classify = new Classificator(SharedPref.getConcertPitch(getActivity()));

        mf = new MedianFilter();

        // launch recording and analysis
        sd = new SharedData(1024);
        recorder = new Recorder(sd, 44100);
        recorder.start();

        int method = SharedPref.getAnlMethod(getActivity()
                .getApplicationContext());

        if (method == METHOD_AMDF)
            analyzer = new AnalyzerAMDF(sd, recorder, this);
        else if (method == METHOD_WAVE)
            analyzer = new AnalyzerWave(sd, recorder, this);

        analyzer.execute();
    }

    @Override
    public void onValueChange(TouchControl t, int val) {

    }

    @Override
    public void onToggle(TouchControl t, int state) {
        switch (state) {
            case TouchControl.STATE_BEGIN:
                circle.setVisibility(View.VISIBLE);
                circle.startAnimation(inAnim);
                break;
            case TouchControl.STATE_TOGGLE:
                // not used

                break;
            case TouchControl.STATE_OUT:
                if (!inAnim.hasEnded())
                    inAnim.cancel();
                circle.startAnimation(outAnim);
                circle.setVisibility(View.INVISIBLE);
                break;
            default:
                break;
        }
    }

    @Override
    public void onPositionChange(TouchControl t, int x, int y) {
        RelativeLayout.LayoutParams pars = (LayoutParams) circle
                .getLayoutParams();
        pars.setMargins(x - circle.getWidth() / 2, y - circle.getHeight() / 2, 0, 0);

        circle.setLayoutParams(pars);
    }

    public void postInformation(Double freq) {
        // classify frequency
        mf.addValue(classify.findTone(freq, temp));

        // return median from the list
        Classificator.Result r = mf.getMedian();

        // show the result
        tunerText.setText(String.format("%s\n%.1f Hz", r.getTone(), r.getFreq()));

        float f;
        if (r.getError() >= 0)
            f = (float) (1 - (r.getError() - 1) * (r.getError() - 1));
        else
            f = (float) ((-r.getError() - 1) * (-r.getError() - 1) - 1);

        bar.setVal(f);
    }
}
