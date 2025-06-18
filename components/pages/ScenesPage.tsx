
import React from 'react';
import { Scene } from '../../types';
import { ICONS } from '../../constants';
import Button from '../common/Button';

interface ScenesPageProps {
  scenes: Scene[];
  activateScene: (sceneId: string) => void;
  activatingSceneId: string | null; // Added prop
}

const ScenesPage: React.FC<ScenesPageProps> = ({ scenes, activateScene, activatingSceneId }) => {
  return (
    <div className="space-y-10">
      <h1 className="text-3xl font-bold tracking-tight text-slate-100 flex items-center mb-10">
        <span className="text-sky-400 mr-3.5 opacity-90">{React.cloneElement(ICONS.WAND as React.ReactElement<any>, {className: "w-9 h-9"})}</span>
        Lighting Scenes
      </h1>

      {scenes.length > 0 ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6 xl:gap-8">
          {scenes.map(scene => (
            <div 
              key={scene.id} 
              className={`bg-slate-800/70 backdrop-blur-lg rounded-xl shadow-xl p-6 flex flex-col justify-between border border-slate-700/50 
                         transition-all duration-300 ease-in-out transform hover:-translate-y-1.5 hover:shadow-2xl hover:shadow-sky-500/25 hover:border-sky-500/60 group
                         ${activatingSceneId === scene.id ? 'animate-pulse-border' : ''}`}
            >
              <div className="flex-grow">
                <div className="flex items-center mb-3.5">
                  {scene.icon && <span className="mr-2.5 text-2xl opacity-80 transition-all duration-300 group-hover:[&_svg]:drop-shadow-[0_0_5px_currentColor]">{React.cloneElement(scene.icon as React.ReactElement<any>, {className: "w-7 h-7"})}</span>}
                  <h2 className="text-xl font-semibold text-slate-50">{scene.name}</h2>
                </div>
                <p className="text-sm text-slate-300 mb-6 min-h-[40px] line-clamp-2 leading-relaxed"> 
                  {scene.description || "Tap to activate this lighting scene."}
                </p>
              </div>
              <Button 
                variant="primary" 
                size="md"
                onClick={() => activateScene(scene.id)}
                className="w-full mt-auto" 
                rightIcon={React.cloneElement(ICONS.SPARKLES as React.ReactElement<any>, {className: "w-4 h-4 opacity-90"})}
                isLoading={activatingSceneId === scene.id} // Optional: show spinner on button
              >
                {activatingSceneId === scene.id ? 'Activating...' : 'Activate Scene'}
              </Button>
            </div>
          ))}
        </div>
      ) : (
        <div className="bg-slate-800/60 backdrop-blur-md rounded-xl shadow-lg p-10 text-center border-2 border-dashed border-slate-700/70">
            <div className="flex justify-center mb-5">
              {React.cloneElement(ICONS.INFO_CIRCLE as React.ReactElement<any>, {className: "w-16 h-16 text-slate-600"})}
            </div>
            <p className="text-slate-300 text-xl font-semibold">No scenes configured yet.</p>
            <p className="text-sm text-slate-400 mt-2.5">Create scenes in settings to quickly set your desired lighting ambiance!</p>
         </div>
      )}
    </div>
  );
};

export default ScenesPage;