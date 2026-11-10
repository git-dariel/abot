type TransferArtworkProps = {
  reversed?: boolean;
  className?: string;
};

export function TransferArtwork({ reversed = false, className = "" }: TransferArtworkProps) {
  const line = reversed ? "#FFFFFF" : "#062456";
  const fill = reversed ? "#8EDCFA" : "#4BBDEB";
  return <svg className={`${className}${reversed ? " reversed" : ""}`} viewBox="0 0 180 120" role="img" aria-label={reversed ? "Incoming file transfer illustration" : "Outgoing file transfer illustration"}>
    <path d="M13 69 164 20l-43 77-36-30-27 22 6-36Z" fill={fill} />
    <path d="m64 53 100-33-79 47m79-47-43 77-36-30-27 22 6-36Z" fill="none" stroke={line} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    <path d="m4 103 54-20" fill="none" stroke={line} strokeWidth="1.5" strokeLinecap="round" opacity=".55" />
    <circle cx="22" cy="31" r="3.5" fill="none" stroke={line} strokeWidth="1.5" />
    <circle cx="145" cy="104" r="2" fill={line} />
  </svg>;
}
