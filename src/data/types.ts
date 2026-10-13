export interface Track {
  id: string;
  title: string;
  artist: string;
  category: string;
  coverArt: string;
  audioSrc: string;
  duration: number;
  bpm: number;
  energy: number;
  lyrics: { uzbek: string; romaji?: string } | null;
  info?: { description: string; details: { title: string; text: string }[] };
}
