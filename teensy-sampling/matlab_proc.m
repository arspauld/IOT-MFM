%% CPE 621 - EMG Muscle Fatigue Monitor
% Alex Spaulding
% Dr. Emil Jovanov
% November 16, 2021

% Clean up output from previous runs
clc; 
clear; 
close all;  

%% Load EMG Data

Fs = 1024;
LOG = csvread('log13.csv');
LOG = LOG(1:10*Fs);
% LOG = LOG - mean(LOG);
figure();
subplot(2, 1, 1)
plot(LOG), title 'Raw EMG Data', xlabel 'Index', ylabel 'Magnitude', axis([ 0 10240 min(LOG) max(LOG)])

% mav_size = Fs/10;
mav_size = 128;

LOG_NORM = LOG;%-mean(LOG);
MAV = zeros(1, length(LOG)/(mav_size));
for i=1:length(MAV)
    MAV(i) = (1/(mav_size)*sum((LOG_NORM((i-1)*mav_size+1 : i*mav_size)).^2))^(1/2);
end
subplot(2, 1, 2)
plot(MAV), title 'MAV';

% RMS = zeros(1, length(LOG)/(Fs));
% for i=1:length(RMS)
%     RMS(i) = (1/(Fs)*sum(LOG((i-1)*Fs+1 : i*Fs)));
% end
% subplot(3, 1, 3)
% plot(RMS), title 'RMS';

% Convert to frequency domain
y_t = LOG(1:Fs);
L = length(y_t);
n = pow2(nextpow2(L));
y_dft = fft(y_t, n);
y_s = fftshift(y_dft);
f = (-n/2:n/2-1)*(Fs/n);

% y_s(Fs/2+1) = 0;

figure()
plot(f, abs(y_s))
title 'Frequency Domain of y(t)'
xlabel 'Frequency (Hz)'
ylabel 'Magnitude'

NFFT=256;FS=1024;

%%
is=1152;
ii=is:(is+NFFT-1);
figure
e=detrend(LOG(ii));
plot(e)
F=abs(fft(e.*hanning(NFFT)));
plot(F)
df=Fs/NFFT;
f=(0:NFFT-1)*df;
plot(f-512,fftshift(F)), title 'Spectrum With Activation', xlabel 'Frequency (Hz)', ylabel 'Magnitude';

%%
is2=5400;
ii2=is2:(is2+NFFT-1);
figure
subplot(2, 1, 1)
es=detrend(LOG(ii2));
F2=abs(fft(es.*hanning(NFFT)));
plot(f-512,fftshift(F2)), title 'Peak1'

psdx = (1/Fs*NFFT)*F2.^2;
psdx(2:end-1) = 2*psdx(2:end-1);
subplot(2, 1, 2);
plot(f,10*log10(psdx))

%%
is3=9900;
ii3=is3:(is3+NFFT-1);
figure
es2=detrend(LOG(ii3));
F3=abs(fft(es2.*hanning(NFFT)));
plot(f-512,fftshift(F3)), title 'Peak2'

%%
is4=3540;
ii4=is4:(is4+NFFT-1);
figure
es3=detrend(LOG(ii4));
F4=abs(fft(es3.*hanning(NFFT)));
% subplot(2, 1, 1)
plot(f-512,fftshift(F4)), title 'Spectrum with No Activation', xlabel 'Frequency (Hz)', ylabel 'Magnitude';

% psdx = (1/Fs*NFFT)*F4.^2;
% psdx(2:end-1) = 2*psdx(2:end-1);
% subplot(2, 1, 2);
% plot(f,10*log10(psdx))

