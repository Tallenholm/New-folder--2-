
import React, { useState, useCallback } from 'react';
import { Light } from '../types';
import { ICONS } from '../constants';
import ToggleSwitch from './common/ToggleSwitch';

interface LightCardProps {
  light: Light;
  onUpdateLight: (id: string, updates: Partial<Light>) => void;
}

const LightCard: React.FC<LightCardProps> = ({ light, onUpdateLight }) => {
  const [isExpanded, setIsExpanded] = useState(false);
  const [displayColor, setDisplayColor] = useState(light.color);
  const [displayBrightness, setDisplayBrightness] = useState(light.brightness);

  React.useEffect(() => {
    setDisplayColor(light.color);
  }, [light.color]);

  React.useEffect(() => {
    setDisplayBrightness(light.brightness);
  }, [light.brightness]);


  const handleToggle = useCallback((checked: boolean) => {
    onUpdateLight(light.id, { isOn: checked });
  }, [light.id, onUpdateLight]);

  const handleCircadianToggle = useCallback((checked: boolean) => {
    onUpdateLight(light.id, { isCircadianEnabled: checked });
  }, [light.id, onUpdateLight]);

  const handleColorChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setDisplayColor(e.target.value); 
    onUpdateLight(light.id, { color: e.target.value });
  };

  const handleBrightnessChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newBrightness = parseInt(e.target.value, 10);
    setDisplayBrightness(newBrightness); 
    onUpdateLight(light.id, { brightness: newBrightness });
  };
  
  // Function to create a darker shade for the gradient
  const getDarkerColor = (hexColor: string, percent: number = 20): string => {
    let r = parseInt(hexColor.slice(1, 3), 16);
    let g = parseInt(hexColor.slice(3, 5), 16);
    let b = parseInt(hexColor.slice(5, 7), 16);

    r = Math.max(0, Math.floor(r * (1 - percent / 100)));
    g = Math.max(0, Math.floor(g * (1 - percent / 100)));
    b = Math.max(0, Math.floor(b * (1 - percent / 100)));

    return `#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}`;
  };

  const effectivePreviewStyle = light.isOn
  ? { backgroundImage: `linear-gradient(to right, ${light.color}, ${getDarkerColor(light.color, 30)})` }
  : { backgroundColor: '#334155' /* slate-700 */ };

  return (
    <div className="bg-slate-800/70 backdrop-blur-lg rounded-xl shadow-xl p-6 transition-all duration-300 hover:shadow-sky-500/25 border border-slate-700/50 hover:border-sky-500/60">
      <div className="flex justify-between items-start mb-5">
        <div>
          <h3 className="text-xl font-semibold text-slate-50">{light.name}</h3>
          <p className={`text-sm font-medium transition-colors ${light.isOn ? 'text-sky-400' : 'text-slate-400'}`}>
            {light.isOn ? `On - ${displayBrightness}%` : 'Off'}
          </p>
        </div>
        <div className="p-1.5 rounded-full bg-slate-700/40 shadow-inner">
          <div style={{ color: light.isOn ? displayColor : 'inherit' }} className="transition-colors duration-300">
            {light.isOn ? React.cloneElement(ICONS.LIGHTBULB_ON as React.ReactElement<any>, {style: {color: displayColor}, className: "w-6 h-6"}) : React.cloneElement(ICONS.LIGHTBULB_OFF as React.ReactElement<any>, {className: "w-6 h-6"})}
          </div>
        </div>
      </div>

      <div className="mb-6">
        <ToggleSwitch
          id={`light-toggle-${light.id}`}
          checked={light.isOn}
          onChange={handleToggle}
          label={<span className="font-medium text-slate-200">Power</span>}
          size="md"
        />
      </div>
      
      <div className={`space-y-5 mb-6 transition-all duration-300 ease-in-out overflow-hidden ${light.isOn ? 'max-h-60 opacity-100' : 'max-h-0 opacity-0'}`}>
         {light.isOn && (
            <>
              <div>
                <label htmlFor={`brightness-${light.id}`} className={`block text-sm font-medium text-slate-300 mb-1.5 ${light.isCircadianEnabled ? 'cursor-not-allowed' : ''}`}>
                  Brightness: <span className="font-semibold text-slate-100">{displayBrightness}%</span>
                </label>
                <input
                  type="range"
                  id={`brightness-${light.id}`}
                  min="0"
                  max="100"
                  value={displayBrightness}
                  onChange={handleBrightnessChange}
                  className={`w-full h-2.5 rounded-lg appearance-none accent-sky-400 transition-all duration-200
                              ${light.isCircadianEnabled ? 'bg-slate-700 opacity-60 cursor-not-allowed' : 'bg-slate-500 cursor-pointer'}`}
                  disabled={light.isCircadianEnabled}
                />
              </div>
              <div className="flex items-center justify-between">
                <label htmlFor={`color-${light.id}`} className={`block text-sm font-medium text-slate-300 ${light.isCircadianEnabled ? 'cursor-not-allowed' : ''}`}>
                  Color
                </label>
                <div className={`relative rounded-md ${light.isCircadianEnabled && light.isOn ? 'disabled-color-pattern cursor-not-allowed' : ''}`}>
                  <input
                    type="color"
                    id={`color-${light.id}`}
                    value={displayColor}
                    onChange={handleColorChange}
                    className={`p-0 w-10 h-10 border-none rounded-md transition-opacity 
                                ${light.isCircadianEnabled && light.isOn ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
                    disabled={light.isCircadianEnabled}
                    style={{ backgroundColor: displayColor }} 
                  />
                </div>
              </div>
            </>
         )}
      </div>
      
      <div className="border-t border-slate-700/60 pt-5 mt-5">
         <ToggleSwitch
          id={`circadian-toggle-${light.id}`}
          checked={light.isCircadianEnabled}
          onChange={handleCircadianToggle}
          label="Circadian Mode"
          size="md"
        />
        {light.isCircadianEnabled && light.isOn && (
          <p className="text-xs text-sky-400 mt-2.5 flex items-center">
            {React.cloneElement(ICONS.INFO_CIRCLE as React.ReactElement<any>, {className: "w-3.5 h-3.5 mr-1.5 opacity-80"})}
            Light settings are automatically managed.
          </p>
        )}
         {light.isCircadianEnabled && !light.isOn && (
          <p className="text-xs text-slate-500 mt-2.5 flex items-center">
            {React.cloneElement(ICONS.INFO_CIRCLE as React.ReactElement<any>, {className: "w-3.5 h-3.5 mr-1.5 opacity-70"})}
            Turn light on for Circadian Mode.
          </p>
        )}
      </div>

       <button 
        onClick={() => setIsExpanded(!isExpanded)} 
        className="text-xs text-sky-400 hover:text-sky-300 font-medium mt-5 flex items-center group -mx-2 px-2 py-1 rounded-md transition-colors duration-200 hover:bg-sky-500/20 group-hover:text-sky-200 focus-visible:ring-1 focus-visible:ring-sky-400 focus-visible:ring-offset-1 focus-visible:ring-offset-slate-800"
        aria-expanded={isExpanded}
        aria-controls={`details-${light.id}`}
      >
        {isExpanded ? 'Hide Details' : 'Show Details'}
        <span className={`ml-1.5 transform transition-transform duration-300 ease-in-out group-hover:translate-x-0.5 ${isExpanded ? 'rotate-180' : ''}`}>
          {React.cloneElement(ICONS.CHEVRON_DOWN as React.ReactElement<any>, {className:"w-3.5 h-3.5"})}
        </span>
      </button>

      <div 
        id={`details-${light.id}`}
        className={`transition-all duration-300 ease-in-out overflow-hidden ${isExpanded ? 'max-h-60 opacity-100 mt-3' : 'max-h-0 opacity-0'}`}
      >
       {isExpanded && (
          <div className="bg-slate-700/50 backdrop-blur-xs rounded-lg p-3.5 text-xs text-slate-300 space-y-1.5 ring-1 ring-inset ring-slate-600/40">
            <p><span className="font-medium text-slate-400">ID:</span> {light.id}</p>
            <p><span className="font-medium text-slate-400">Room ID:</span> {light.roomId || 'N/A'}</p>
            <p><span className="font-medium text-slate-400">Set Color:</span> <span style={{color: light.color, textShadow: '0 0 5px rgba(0,0,0,0.5)'}}>{light.color}</span></p>
            <p><span className="font-medium text-slate-400">Set Brightness:</span> {light.brightness}%</p>
            <div className="font-medium text-slate-300 pt-1.5">Effective Preview:</div>
            <div className="w-full h-3.5 rounded-sm mt-1 shadow-md shadow-black/30" style={effectivePreviewStyle}></div>
          </div>
        )}
      </div>
    </div>
  );
};

export default LightCard;