"use client";

import { useParams } from "next/navigation";
import { Suspense } from "react";
import { Loading } from "@/views/ui";
import { VideoFormPage } from "../VideoFormPage";

// Avec cacheComponents, useParams suspend sur une route dynamique : il faut une frontière Suspense.
export default function EditVideoPage() {
  return (
    <Suspense fallback={<Loading />}>
      <EditVideo />
    </Suspense>
  );
}

function EditVideo() {
  const { id } = useParams<{ id: string }>();
  // key : un autre identifiant repart d'un formulaire vierge.
  return <VideoFormPage key={id} videoId={id} />;
}
