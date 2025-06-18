
import { GoogleGenAI, GenerateContentResponse } from "@google/genai";
import { GEMINI_MODEL_TEXT } from '../constants';

// Ensure API_KEY is available in the environment.
// The build process or environment setup (e.g., .env file with Vite/CRA)
// should make process.env.API_KEY accessible.
const apiKey = process.env.API_KEY;

if (!apiKey) {
  console.error("API_KEY for Gemini is not set. Please set the environment variable.");
  // In a real app, you might throw an error or handle this more gracefully,
  // but for this example, we'll let it proceed and fail at API call time if key is truly missing.
}

const ai = new GoogleGenAI({ apiKey: apiKey || "MISSING_API_KEY" }); // Fallback to allow app to load if key is missing

export const getGeminiSuggestions = async (prompt: string): Promise<string> => {
  if (!apiKey) {
    return Promise.reject("Gemini API Key is not configured.");
  }
  try {
    const response: GenerateContentResponse = await ai.models.generateContent({
      model: GEMINI_MODEL_TEXT,
      contents: prompt,
       // Example config, adjust as needed
      config: { 
        temperature: 0.7,
        topP: 0.9,
        topK: 40,
        // Omit thinkingConfig to use default (thinking enabled) for higher quality.
        // For very low latency needs, one could add:
        // thinkingConfig: { thinkingBudget: 0 }
      }
    });
    
    // Direct access to text as per guidelines
    return response.text;

  } catch (error) {
    console.error('Error calling Gemini API:', error);
    if (error instanceof Error) {
        return Promise.reject(`Error generating content: ${error.message}`);
    }
    return Promise.reject('An unknown error occurred while calling Gemini API.');
  }
};
