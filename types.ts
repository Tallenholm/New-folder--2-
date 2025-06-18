
export interface Light {
  id: string;
  name: string;
  isOn: boolean;
  brightness: number; // 0-100
  color: string; // hex string e.g., "#FF0000"
  isCircadianEnabled: boolean;
  roomId: string | null; // Added: ID of the room the light belongs to
}

export interface ChatMessage {
  id: string;
  sender: 'user' | 'ai';
  text: string;
  timestamp: Date;
}

export interface CircadianPoint {
  time: string; // e.g., "06:00"
  hour: number; // 0-23
  colorTemperature: number; // Kelvin
  brightnessFactor: number; // 0-1 (relative brightness)
  colorHex: string; // Representative color for the chart
}

export interface Room {
  id: string;
  name: string;
  icon?: React.ReactNode; // Optional: for a room icon
}

export interface Scene {
  id: string;
  name: string;
  description: string;
  icon?: React.ReactNode; // Optional: for a scene icon
  lightSettings: Array<{ lightId: string | 'all'; settings: Partial<Pick<Light, 'isOn' | 'brightness' | 'color' | 'isCircadianEnabled'>> }>;
}
