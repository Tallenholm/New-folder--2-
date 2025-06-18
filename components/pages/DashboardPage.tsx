
import React from 'react';
import LightCard from '../LightCard';
import CircadianChart from '../CircadianChart';
import GeminiAssistant from '../GeminiAssistant';
import { Light } from '../../types';
import { ICONS } from '../../constants';

interface DashboardPageProps {
  lights: Light[];
  updateLight: (id: string, updates: Partial<Light>) => void;
  currentHour: number; 
}

const DashboardPage: React.FC<DashboardPageProps> = ({ lights, updateLight }) => {
  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 xl:gap-10 items-start">
      <section className="lg:col-span-2 space-y-10">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-slate-100 mb-8 flex items-center">
            <span className="text-yellow-400 mr-3 opacity-90">{React.cloneElement(ICONS.LIGHTBULB_ON as React.ReactElement<any>, {className: "w-9 h-9"})}</span>
            Your Lights Overview
          </h1>
          {lights.length === 0 ? (
            <div className="bg-slate-800/60 backdrop-blur-md rounded-xl shadow-lg p-8 text-center border-2 border-dashed border-slate-700/70 
                            bg-[radial-gradient(circle_at_center,_rgba(45,55,72,0.08)_1px,_transparent_1px)] [background-size:12px_12px]">
                <div className="flex justify-center mb-5">
                  {React.cloneElement(ICONS.INFO_CIRCLE as React.ReactElement<any>, {className: "w-14 h-14 text-slate-600"})}
                </div>
                <p className="text-slate-300 text-xl font-semibold">No lights configured yet.</p>
                <p className="text-sm text-slate-400 mt-2.5">
                  Please add lights via the settings page or connect to your smart home system to get started.
                </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6 xl:gap-8">
              {lights.map(light => (
                <LightCard key={light.id} light={light} onUpdateLight={updateLight} />
              ))}
            </div>
          )}
        </div>
      </section>

      <aside className="lg:col-span-1 space-y-8 xl:space-y-10 sticky top-24">
        <CircadianChart />
        <GeminiAssistant />
      </aside>
    </div>
  );
};

export default DashboardPage;