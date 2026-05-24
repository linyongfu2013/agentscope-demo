type Props = {
  url: string;
  mimeType?: string;
};

export function AudioMessage({ url, mimeType }: Props) {
  return (
    <audio controls className="w-full" aria-label="Audio message">
      <source src={url} type={mimeType} />
    </audio>
  );
}
