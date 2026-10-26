import Image from "next/image";

// 글 사진 (lib/photos.ts). 비율은 감싼 요소의 CSS(aspect-ratio)가 정하고, 사진은 그 칸을 채운다.
export function Photo({ src, className, sizes, priority }: { src: string; className: string; sizes: string; priority?: boolean }) {
  return (
    <span className={`photo ${className}`}>
      <Image src={src} alt="" fill sizes={sizes} priority={priority} />
    </span>
  );
}
