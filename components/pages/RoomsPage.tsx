
import React from 'react';
import { Room, Light } from '../../types';
import { ICONS } from '../../constants';
import LightCard from '../LightCard'; 

interface RoomsPageProps {
  rooms: Room[];
  lights: Light[];
  updateLight: (id: string, updates: Partial<Light>) => void;
}

const RoomsPage: React.FC<RoomsPageProps> = ({ rooms, lights, updateLight }) => {
  const getLightsInRoom = (roomId: string | null) => {
    return lights.filter(light => light.roomId === roomId);
  };

  const lightsWithoutRoom = getLightsInRoom(null);

  return (
    <div className="space-y-12">
      <h1 className="text-3xl font-bold tracking-tight text-slate-100 flex items-center mb-10">
        <span className="text-sky-400 mr-3.5 opacity-90">{React.cloneElement(ICONS.ROOM_DOOR as React.ReactElement<any>, {className: "w-9 h-9"})}</span>
        Manage Lights by Room
      </h1>

      {rooms.map(room => {
        const roomLights = getLightsInRoom(room.id);
        return (
          <section 
            key={room.id} 
            aria-labelledby={`room-title-${room.id}`} 
            className="bg-slate-800/70 backdrop-blur-lg rounded-xl shadow-xl p-6 md:p-8 border border-slate-700/50 hover:border-sky-500/50 transition-all duration-300 group"
          >
            <div className="flex items-center border-b border-slate-700/70 pb-5 mb-6 transition-transform duration-300 group-hover:scale-[1.02]">
              {room.icon && <span className="mr-3.5 text-3xl opacity-80">{React.cloneElement(room.icon as React.ReactElement<any>, {className: "w-8 h-8"})}</span>}
              <h2 id={`room-title-${room.id}`} className="text-2xl font-semibold text-slate-100">{room.name}</h2>
            </div>
            {roomLights.length > 0 ? (
              <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
                {roomLights.map(light => (
                  <LightCard key={light.id} light={light} onUpdateLight={updateLight} />
                ))}
              </div>
            ) : (
              <div className="text-center py-6 text-slate-400">
                <div className="flex justify-center mb-3">
                 {React.cloneElement(ICONS.INFO_CIRCLE as React.ReactElement<any>, {className: "w-10 h-10 text-slate-500"})}
                </div>
                <p className="text-md">No lights currently assigned to this room.</p>
              </div>
            )}
          </section>
        );
      })}

      {lightsWithoutRoom.length > 0 && (
        <section 
          aria-labelledby="unassigned-lights-title" 
          className="bg-slate-800/70 backdrop-blur-lg rounded-xl shadow-xl p-6 md:p-8 border border-slate-700/50 hover:border-sky-500/50 transition-colors duration-300 mt-12 group"
        >
           <div className="flex items-center border-b border-slate-700/70 pb-5 mb-6 transition-transform duration-300 group-hover:scale-[1.02]">
             <h2 id="unassigned-lights-title" className="text-2xl font-semibold text-slate-100">Unassigned Lights</h2>
           </div>
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
            {lightsWithoutRoom.map(light => (
              <LightCard key={light.id} light={light} onUpdateLight={updateLight} />
            ))}
          </div>
        </section>
      )}

      {rooms.length === 0 && lightsWithoutRoom.length === 0 && (
         <div className="bg-slate-800/60 backdrop-blur-md rounded-xl shadow-lg p-10 text-center border-2 border-dashed border-slate-700/70">
            <div className="flex justify-center mb-5">
              {React.cloneElement(ICONS.INFO_CIRCLE as React.ReactElement<any>, {className: "w-16 h-16 text-slate-600"})}
            </div>
            <p className="text-slate-300 text-xl font-semibold">No rooms or lights found.</p>
            <p className="text-sm text-slate-400 mt-2.5">Start by adding rooms and assigning lights in the settings.</p>
         </div>
      )}
    </div>
  );
};

export default RoomsPage;