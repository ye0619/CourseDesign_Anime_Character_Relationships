export interface CharacterCrop { mode:'portrait'|'full'; focalX:number; focalY:number; scale:number; imageFingerprint:string; imageUrl?:string }
export interface CharacterEdit { uid:string; version:number; schemaVersion:1; sourceVersion:string; fields:Record<string,unknown>; portraits:Record<string,CharacterCrop> }
