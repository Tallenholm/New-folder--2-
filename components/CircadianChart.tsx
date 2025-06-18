
import React, { useMemo } from 'react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend, ReferenceLine, DotProps as RechartsDotProps } from 'recharts';
import { CIRCADIAN_DATA } from '../constants';
import { CircadianPoint } from '../types';

interface ChartDataPoint {
  name: string;
  hour: number;
  kelvin: number;
  color: string; // This is colorHex from CIRCADIAN_DATA
  brightnessFactor: number;
}

const CustomTooltip: React.FC<any> = ({ active, payload, label }) => {
  if (active && payload && payload.length) {
    const data = payload[0].payload as ChartDataPoint;
    return (
      <div className="bg-slate-700/80 backdrop-blur-lg p-3.5 rounded-xl shadow-xl border border-slate-600/70">
        <p className="text-sm font-semibold text-white mb-1.5">{`Time: ${data.name}`}</p>
        <p className="text-xs text-slate-200">{`Color Temp: ${data.kelvin}K`}</p>
        <p className="text-xs text-slate-300">{`Brightness: ${Math.round(data.brightnessFactor * 100)}%`}</p>
        <div className="flex items-center mt-2">
          <span className="text-xs text-slate-300 mr-1.5">Color:</span>
          <div style={{ width: '14px', height: '14px', backgroundColor: data.color, borderRadius: '4px', border: '1px solid rgba(255,255,255,0.4)' }}></div>
        </div>
      </div>
    );
  }
  return null;
};

interface CustomDotProps extends Omit<RechartsDotProps, 'payload' | 'fill'> {
  payload?: ChartDataPoint;
  color?: string; 
  fill?: string; 
}

const CustomizedDot: React.FC<CustomDotProps> = (props) => {
  const { cx, cy, stroke: rechartsStroke, fill: rechartsFill, payload, color: refLineColor } = props;
  const dotFillColor = refLineColor || payload?.color || rechartsFill || '#38bdf8'; 
  const dotStrokeColor = payload?.color ? 'rgba(255,255,255,0.6)' : rechartsStroke || '#0ea5e9';
  const dotStrokeWidth = payload?.color ? 1.5 : 1;

  if (cx === undefined || cy === undefined) return null;
  return <circle cx={cx} cy={cy} r={5} stroke={dotStrokeColor} strokeWidth={dotStrokeWidth} fill={dotFillColor} fillOpacity={1} />;
};


const CircadianChart: React.FC = () => {
  const chartData: ChartDataPoint[] = useMemo(() => {
    return CIRCADIAN_DATA.map(p => ({
      name: p.time,
      hour: p.hour,
      kelvin: p.colorTemperature,
      color: p.colorHex,
      brightnessFactor: p.brightnessFactor,
    })).sort((a,b) => a.hour - b.hour);
  }, []);

  const loopedChartData = useMemo(() => {
    const firstPoint = chartData[0];
    if(firstPoint) {
       return [...chartData, {...firstPoint, name: "24:00", hour: 24, kelvin: firstPoint.kelvin, color: firstPoint.color, brightnessFactor: firstPoint.brightnessFactor}];
    }
    return chartData;
  }, [chartData]);


  const currentHour = new Date().getHours();
  const currentMinute = new Date().getMinutes();
  const currentTimeDecimal = currentHour + currentMinute / 60;

  let currentTimeLabel = chartData.find(d => d.hour === currentHour)?.name;
  if (!currentTimeLabel) { 
      const closestPoint = chartData.reduce((prev, curr) => 
          (Math.abs(curr.hour - currentTimeDecimal) < Math.abs(prev.hour - currentTimeDecimal) ? curr : prev)
      );
      currentTimeLabel = closestPoint.name;
  }


  return (
    <div className="bg-slate-800/70 backdrop-blur-lg p-4 sm:p-6 rounded-xl shadow-xl border border-slate-700/50">
      <h3 className="text-xl font-semibold text-slate-100 mb-1">Circadian Cycle</h3>
      <p className="text-xs text-slate-400 mb-6">Light color temperature & intensity over 24 hours.</p>
      <ResponsiveContainer width="100%" height={300}>
        <LineChart data={loopedChartData} margin={{ top: 10, right: 15, left: -20, bottom: 5 }}>
          <CartesianGrid 
            strokeDasharray="2 5" 
            strokeOpacity={0.15} 
            stroke="#526178" // Slightly lighter slate
          />
          <XAxis 
            dataKey="name" 
            tick={{ fill: '#94a3b8', fontSize: 11 }} // slate-400
            stroke="#475569" // slate-600
            interval="preserveStartEnd"
            ticks={["00:00", "03:00", "06:00", "09:00", "12:00", "15:00", "18:00", "21:00", "24:00"]}
          />
          <YAxis 
            yAxisId="left" 
            orientation="left" 
            stroke="#475569" // slate-600
            tick={{ fill: '#94a3b8', fontSize: 11 }} // slate-400
            label={{ value: 'Kelvin (K)', angle: -90, position: 'insideLeft', fill: '#94a3b8', fontSize: 12, dx: -15, dy: 30 }}
            domain={[1500, 6500]}
          />
          <Tooltip 
            content={<CustomTooltip />} 
            cursor={{ stroke: '#38bdf8', strokeWidth: 1.5, strokeDasharray: '3 3' }} // sky-400
          />
          <Legend 
            wrapperStyle={{ color: '#cbd5e1', fontSize: '12px', paddingTop: '15px' }} // slate-300
            iconSize={10}
            formatter={(value) => <span className="text-slate-300">{value}</span>}
          />
          <Line 
            yAxisId="left" 
            type="monotone" 
            dataKey="kelvin" 
            stroke="#38bdf8" // sky-400 for the line itself
            strokeWidth={3} 
            dot={<CustomizedDot />}
            activeDot={{ r: 8, stroke: '#0ea5e9', fill: 'rgba(14, 165, 233, 0.4)', strokeWidth: 2 }} // sky-500 stroke
            name="Color Temperature"
          />
          <ReferenceLine 
            x={currentTimeLabel || chartData[0]?.name}
            stroke="#f59e0b" // amber-500 for a vibrant "Now" line
            strokeDasharray="4 4"
            strokeWidth={2.5}
            label={{ value: "Now", position: 'top', fill: '#fbbf24', fontSize: 12, fontWeight: '600', dy: -10 }} // amber-400 label
            ifOverflow="extendDomain"
            yAxisId="left"
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
};

export default CircadianChart;